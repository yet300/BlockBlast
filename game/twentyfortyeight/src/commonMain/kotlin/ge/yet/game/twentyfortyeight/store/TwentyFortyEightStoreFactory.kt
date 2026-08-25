package ge.yet.game.twentyfortyeight.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import dev.zacsweers.metro.Inject
import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.twentyfortyeight.analytics.AnalyticsBucketPolicy
import ge.yet.game.twentyfortyeight.analytics.AnalyticsFact
import ge.yet.game.twentyfortyeight.analytics.RestartSource
import ge.yet.game.twentyfortyeight.audio.AudioEvent
import ge.yet.game.twentyfortyeight.diagnostics.InvariantCode
import ge.yet.game.twentyfortyeight.diagnostics.TwentyFortyEightFailure
import ge.yet.game.twentyfortyeight.engine.AudioControlPolicy
import ge.yet.game.twentyfortyeight.engine.AudioControls
import ge.yet.game.twentyfortyeight.engine.CounterOverflowException
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameRules
import ge.yet.game.twentyfortyeight.engine.GameState
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.MoveEngine
import ge.yet.game.twentyfortyeight.engine.MoveFailure
import ge.yet.game.twentyfortyeight.engine.MoveInput
import ge.yet.game.twentyfortyeight.engine.MoveResult
import ge.yet.game.twentyfortyeight.engine.ResultSnapshot
import ge.yet.game.twentyfortyeight.engine.RngState
import ge.yet.game.twentyfortyeight.engine.RulesState
import ge.yet.game.twentyfortyeight.engine.TutorialCompletionReason
import ge.yet.game.twentyfortyeight.engine.UndoResult
import ge.yet.game.twentyfortyeight.persistence.CheckpointResult
import ge.yet.game.twentyfortyeight.persistence.GameCommit
import ge.yet.game.twentyfortyeight.persistence.LoadResult
import ge.yet.game.twentyfortyeight.persistence.RestoredGameData
import ge.yet.game.twentyfortyeight.persistence.SessionPersistenceCoordinator
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

@Inject
internal class TwentyFortyEightStoreFactory(
    private val storeFactory: StoreFactory,
    private val engine: MoveEngine,
    private val coordinator: SessionPersistenceCoordinator,
    private val visibility: MiniAppVisibilitySource,
    private val seedSource: NewGameSeedSource,
) {
    fun create(): TwentyFortyEightStore =
        object :
            TwentyFortyEightStore,
            Store<TwentyFortyEightStore.Intent, TwentyFortyEightStore.State, TwentyFortyEightStore.Label> by
                storeFactory.create(
                    name = "TwentyFortyEightStore",
                    initialState = TwentyFortyEightStore.State(visibility = visibility.visibility.value),
                    bootstrapper = SimpleBootstrapper(Action.Init),
                    executorFactory = ::ExecutorImpl,
                    reducer = ReducerImpl,
                ) {}

    private sealed interface Action {
        data object Init : Action
    }

    private sealed interface Msg {
        data class BootstrapReady(
            val game: GameState,
            val statistics: GameStatistics,
            val tutorialSeen: Boolean,
            val overlay: OverlayState?,
            val revision: Long,
        ) : Msg

        data class Authoritative(
            val game: GameState,
            val statistics: GameStatistics,
            val tutorialSeen: Boolean,
            val transition: VisualTransition?,
            val overlay: OverlayState?,
        ) : Msg

        data class QueueDirection(val direction: Direction) : Msg
        data object ClearTransitionAndQueue : Msg
        data class OverlayChanged(val overlay: OverlayState?) : Msg
        data class VisibilityChanged(val visibility: MiniAppVisibility) : Msg
        data class CheckpointRequested(val revision: Long) : Msg
        data class CheckpointStored(val revision: Long) : Msg
        data class CheckpointFailed(val revision: Long) : Msg
        data class BarrierFailed(val visibleRevision: Long) : Msg

        data class NewGameCommitted(
            val rules: RulesState,
            val revision: Long,
        ) : Msg
    }

    private data class PendingExternalLabel(
        val reservationRevision: Long,
        val label: TwentyFortyEightStore.Label,
    )

    private object ReducerImpl : Reducer<TwentyFortyEightStore.State, Msg> {
        override fun TwentyFortyEightStore.State.reduce(msg: Msg): TwentyFortyEightStore.State =
            when (msg) {
                is Msg.BootstrapReady -> copy(
                    bootstrap = BootstrapState.Ready,
                    game = msg.game,
                    statistics = msg.statistics,
                    tutorialSeen = msg.tutorialSeen,
                    overlay = msg.overlay,
                    requestedRevision = msg.revision,
                    durableRevision = msg.revision,
                    persistenceDirty = false,
                )
                is Msg.Authoritative -> copy(
                    game = msg.game,
                    statistics = msg.statistics,
                    tutorialSeen = msg.tutorialSeen,
                    activeTransition = msg.transition,
                    overlay = msg.overlay,
                )
                is Msg.QueueDirection -> copy(pendingDirection = msg.direction)
                Msg.ClearTransitionAndQueue -> copy(activeTransition = null, pendingDirection = null)
                is Msg.OverlayChanged -> copy(overlay = msg.overlay)
                is Msg.VisibilityChanged -> copy(visibility = msg.visibility)
                is Msg.CheckpointRequested -> copy(requestedRevision = msg.revision)
                is Msg.CheckpointStored -> copy(
                    durableRevision = maxOf(durableRevision, msg.revision),
                    persistenceDirty = if (msg.revision >= requestedRevision) false else persistenceDirty,
                )
                is Msg.CheckpointFailed -> if (msg.revision == requestedRevision) {
                    copy(persistenceDirty = true)
                } else {
                    this
                }
                is Msg.BarrierFailed -> copy(
                    persistenceDirty = persistenceDirty || msg.visibleRevision > durableRevision,
                )
                is Msg.NewGameCommitted -> copy(
                    game = msg.rules.game,
                    statistics = msg.rules.statistics,
                    activeTransition = null,
                    pendingDirection = null,
                    overlay = null,
                    durableRevision = maxOf(durableRevision, msg.revision),
                    persistenceDirty = false,
                )
            }
    }

    private inner class ExecutorImpl : CoroutineExecutor<
        TwentyFortyEightStore.Intent,
        Action,
        TwentyFortyEightStore.State,
        Msg,
        TwentyFortyEightStore.Label,
        >() {
        private var nextTransitionId = 1L
        private var tutorialReason: TutorialCompletionReason? = null
        private var lastControls: AudioControls? = null
        private var destructivePending = false
        private var pendingRestartSource: RestartSource? = null
        private val pendingExternalLabels = ArrayDeque<PendingExternalLabel>(MAX_PENDING_EXTERNAL_LABELS)

        override fun executeAction(action: Action) {
            when (action) {
                Action.Init -> initialize()
            }
        }

        override fun executeIntent(intent: TwentyFortyEightStore.Intent) {
            if (destructivePending && intent !is TwentyFortyEightStore.Intent.VisibilityChanged) return
            if (intent.requiresPlayingPhase() && state().game?.phase != GamePhase.Playing) return
            when (intent) {
                is TwentyFortyEightStore.Intent.Move -> move(intent.direction)
                TwentyFortyEightStore.Intent.Undo -> undo()
                TwentyFortyEightStore.Intent.RequestRestart -> requestRestart()
                TwentyFortyEightStore.Intent.ConfirmRestart -> confirmRestart()
                TwentyFortyEightStore.Intent.CancelOverlay -> cancelOverlay()
                TwentyFortyEightStore.Intent.ContinueAfterVictory -> continueAfterVictory()
                TwentyFortyEightStore.Intent.NewGameFromResult -> newGameFromResult()
                TwentyFortyEightStore.Intent.SkipTutorial -> skipTutorial()
                is TwentyFortyEightStore.Intent.AnimationCompleted -> animationCompleted(intent.transitionId)
                is TwentyFortyEightStore.Intent.VisibilityChanged ->
                    dispatch(Msg.VisibilityChanged(intent.visibility))
                TwentyFortyEightStore.Intent.OpenStatistics -> openStatistics()
            }
        }

        private fun initialize() {
            scope.launch {
                visibility.visibility.collect { current ->
                    dispatch(Msg.VisibilityChanged(current))
                }
            }
            scope.launch {
                yield()
                when (val result = coordinator.load()) {
                    is LoadResult.Failed -> {
                        publish(TwentyFortyEightStore.Label.Diagnostic(result.failure))
                        bootstrapFresh(emptyRestoredGameData())
                    }
                    is LoadResult.Loaded -> {
                        result.validationFailures.forEach { failure ->
                            publish(TwentyFortyEightStore.Label.Diagnostic(failure))
                        }
                        tutorialReason = result.data.tutorialReason
                        val restored = result.data.game
                        if (restored == null) {
                            bootstrapFresh(result.data)
                        } else {
                            val game = restored.copy(bestScore = maxOf(restored.bestScore, result.data.bestScore))
                            val overlay = if (
                                game.phase == GamePhase.Playing &&
                                game.facts.victoryReached &&
                                !game.facts.victoryAcknowledged
                            ) {
                                OverlayState.Victory
                            } else {
                                null
                            }
                            dispatch(
                                Msg.BootstrapReady(
                                    game = game,
                                    statistics = result.data.statistics,
                                    tutorialSeen = result.data.tutorialSeen,
                                    overlay = overlay,
                                    revision = result.data.revision,
                                ),
                            )
                            publishAudioBootstrap(game)
                            if (game.phase == GamePhase.Playing) {
                                publish(
                                    TwentyFortyEightStore.Label.Analytics(
                                        AnalyticsFact.GameResumed(game.runOrdinal),
                                    ),
                                )
                            } else {
                                publishResult(game, result.data.statistics)
                            }
                        }
                    }
                }
            }
        }

        private fun bootstrapFresh(data: RestoredGameData) {
            tutorialReason = data.tutorialReason
            val rules = freshRules(data)
            dispatch(
                Msg.BootstrapReady(
                    game = rules.game,
                    statistics = rules.statistics,
                    tutorialSeen = data.tutorialSeen,
                    overlay = null,
                    revision = data.revision,
                ),
            )
            publishAudioBootstrap(rules.game)
            publish(TwentyFortyEightStore.Label.Audio(AudioEvent.TileSpawn))
            submitCheckpoint(
                listOf(
                    TwentyFortyEightStore.Label.Analytics(
                        AnalyticsFact.GameStarted(rules.game.runOrdinal),
                    ),
                ),
            )
        }

        private fun freshRules(data: RestoredGameData): RulesState {
            val fresh = GameRules.newGame(previous = null, seed = nextSeed())
            val gamesStarted = if (data.statistics.gamesStarted == Long.MAX_VALUE) {
                publishRevisionDiagnostic(InvariantCode.CounterOverflow)
                Long.MAX_VALUE
            } else {
                data.statistics.gamesStarted + 1L
            }
            val highest = fresh.game.board.values().filterNotNull().maxOrNull() ?: 0L
            return RulesState(
                game = fresh.game.copy(
                    runOrdinal = maxOf(1L, gamesStarted),
                    bestScore = maxOf(data.bestScore, fresh.game.score),
                ),
                statistics = data.statistics.copy(
                    gamesStarted = gamesStarted,
                    highestTileEver = maxOf(data.statistics.highestTileEver, highest),
                ),
            )
        }

        private fun publishAudioBootstrap(game: GameState) {
            publish(TwentyFortyEightStore.Label.AudioStart)
            val controls = AudioControlPolicy.from(game)
            lastControls = controls
            publish(TwentyFortyEightStore.Label.AudioControlsChanged(controls))
        }

        private fun move(direction: Direction) {
            val current = state()
            if (!current.acceptsGameInput() || current.overlay != null) return
            if (current.activeTransition != null) {
                if (current.pendingDirection == null) dispatch(Msg.QueueDirection(direction))
                return
            }
            val game = current.game ?: return
            when (
                val result = engine.apply(
                    input = MoveInput(
                        board = game.board,
                        score = game.score,
                        rng = game.rng,
                        nextTileId = game.nextTileId,
                        victoryAlreadyReached = game.facts.victoryReached,
                    ),
                    direction = direction,
                    transitionId = allocateTransition() ?: run {
                        publishRevisionDiagnostic(InvariantCode.IdentityOverflow)
                        return
                    },
                )
            ) {
                is MoveResult.Unchanged -> Unit
                is MoveResult.Failed -> publish(
                    TwentyFortyEightStore.Label.Diagnostic(
                        TwentyFortyEightFailure.InvariantViolation(
                            when (result.reason) {
                                MoveFailure.ScoreOverflow -> InvariantCode.ScoreOverflow
                                MoveFailure.IdentityOverflow -> InvariantCode.IdentityOverflow
                            },
                        ),
                    ),
                )
                is MoveResult.Changed -> if (nextRevision() == null) {
                    publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                } else {
                    acceptMove(current, result)
                }
            }
        }

        private fun acceptMove(
            before: TwentyFortyEightStore.State,
            result: MoveResult.Changed,
        ) {
            val beforeGame = checkNotNull(before.game)
            val rules = try {
                GameRules.finishIfTerminal(
                    GameRules.acceptChanged(RulesState(beforeGame, before.statistics), result),
                )
            } catch (_: CounterOverflowException) {
                publishRevisionDiagnostic(InvariantCode.CounterOverflow)
                return
            }
            val tutorialCompleted = !before.tutorialSeen
            if (tutorialCompleted) tutorialReason = TutorialCompletionReason.Move
            val terminal = rules.game.phase == GamePhase.GameOver
            val victory = result.victory == ge.yet.game.twentyfortyeight.engine.VictoryTransition.FirstReached
            val overlay = when {
                terminal -> null
                victory -> OverlayState.Victory
                else -> before.overlay
            }
            dispatch(
                Msg.Authoritative(
                    game = rules.game,
                    statistics = rules.statistics,
                    tutorialSeen = before.tutorialSeen || tutorialCompleted,
                    transition = if (terminal) null else VisualTransition.Move(result.transitionId, result),
                    overlay = overlay,
                ),
            )

            publish(
                TwentyFortyEightStore.Label.Audio(
                    AudioEvent.MoveResolved(
                        spawned = true,
                        mergeValues = result.merges.map { it.resultValue },
                    ),
                ),
            )
            publish(
                TwentyFortyEightStore.Label.Announcement(
                    AnnouncementFact.Move(
                        scoreDelta = result.scoreDelta,
                        largestMerge = result.merges.maxOfOrNull { it.resultValue.value },
                    ),
                ),
            )
            val external = mutableListOf<TwentyFortyEightStore.Label>()
            if (tutorialCompleted) {
                external += TwentyFortyEightStore.Label.Analytics(
                    AnalyticsFact.TutorialCompleted(skipped = false),
                )
            }
            if (rules.game.bestScore > beforeGame.bestScore) {
                publish(TwentyFortyEightStore.Label.Audio(AudioEvent.NewBest))
                publish(TwentyFortyEightStore.Label.Announcement(AnnouncementFact.NewBest(rules.game.bestScore)))
                external += TwentyFortyEightStore.Label.Analytics(
                    AnalyticsFact.NewBest(AnalyticsBucketPolicy.score(rules.game.score)),
                )
            }
            (rules.game.facts.milestoneReservations - beforeGame.facts.milestoneReservations)
                .sorted()
                .forEach { value ->
                    external += TwentyFortyEightStore.Label.Analytics(AnalyticsFact.MilestoneReached(value))
                }
            if (victory) {
                publish(TwentyFortyEightStore.Label.Audio(AudioEvent.Victory))
                publish(TwentyFortyEightStore.Label.Announcement(AnnouncementFact.Victory))
                publish(TwentyFortyEightStore.Label.Focus(FocusTarget.Victory))
                external += TwentyFortyEightStore.Label.Analytics(
                    AnalyticsFact.Victory(AnalyticsBucketPolicy.score(rules.game.score)),
                )
                external += TwentyFortyEightStore.Label.Review(
                    MiniAppReviewOpportunity(
                        triggerId = FIRST_VICTORY_TRIGGER,
                        score = rules.game.score,
                        bestScore = rules.game.bestScore,
                    ),
                )
            }
            if (terminal) {
                publish(TwentyFortyEightStore.Label.Audio(AudioEvent.GameOver))
                publish(TwentyFortyEightStore.Label.Announcement(AnnouncementFact.GameOver))
                publishResult(rules.game, rules.statistics)
                external += TwentyFortyEightStore.Label.Analytics(
                    AnalyticsFact.GameOver(AnalyticsBucketPolicy.score(rules.game.score)),
                )
            }
            publishControlsIfChanged(rules.game)
            submitCheckpoint(external)
        }

        private fun undo() {
            val current = state()
            if (!current.acceptsGameInput() || current.overlay != null || current.activeTransition != null) return
            val game = current.game ?: return
            val undo = try {
                GameRules.undo(RulesState(game, current.statistics))
            } catch (_: CounterOverflowException) {
                publishRevisionDiagnostic(InvariantCode.CounterOverflow)
                return
            }
            when (val result = undo) {
                UndoResult.Unavailable -> Unit
                is UndoResult.Changed -> {
                    if (nextRevision() == null) {
                        publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                        return
                    }
                    val transitionId = allocateTransition() ?: run {
                        publishRevisionDiagnostic(InvariantCode.IdentityOverflow)
                        return
                    }
                    val transition = VisualTransition.Undo(transitionId, result.transition)
                    dispatch(
                        Msg.Authoritative(
                            game = result.state.game,
                            statistics = result.state.statistics,
                            tutorialSeen = current.tutorialSeen,
                            transition = transition,
                            overlay = null,
                        ),
                    )
                    publish(TwentyFortyEightStore.Label.Audio(AudioEvent.Undo))
                    publishControlsIfChanged(result.state.game)
                    submitCheckpoint(
                        listOf(
                            TwentyFortyEightStore.Label.Analytics(
                                AnalyticsFact.UndoUsed(result.state.game.runOrdinal),
                            ),
                        ),
                    )
                }
            }
        }

        private fun animationCompleted(id: Long) {
            val transition = state().activeTransition ?: return
            if (transition.transitionId != id) return
            val queued = state().pendingDirection
            dispatch(Msg.ClearTransitionAndQueue)
            if (queued != null) move(queued)
        }

        private fun openStatistics() {
            val current = state()
            if (!current.acceptsGameInput() || current.overlay != null || current.activeTransition != null) return
            dispatch(Msg.OverlayChanged(OverlayState.Statistics))
        }

        private fun cancelOverlay() {
            if (!state().acceptsGameInput()) return
            if (destructivePending) return
            when (state().overlay) {
                OverlayState.Victory -> continueAfterVictory()
                OverlayState.RestartConfirmation -> {
                    val restored = if (pendingRestartSource == RestartSource.Victory) {
                        OverlayState.Victory
                    } else {
                        null
                    }
                    dispatch(Msg.OverlayChanged(restored))
                    pendingRestartSource = null
                    publish(
                        TwentyFortyEightStore.Label.Focus(
                            if (restored == OverlayState.Victory) FocusTarget.Victory else FocusTarget.Board,
                        ),
                    )
                }
                OverlayState.Statistics -> {
                    dispatch(Msg.OverlayChanged(null))
                    publish(TwentyFortyEightStore.Label.Focus(FocusTarget.Board))
                }
                null -> Unit
            }
        }

        private fun requestRestart() {
            val current = state()
            if (!current.acceptsGameInput() || current.activeTransition != null) return
            if (current.overlay != null && current.overlay != OverlayState.Victory) return
            val game = current.game ?: return
            val source = if (current.overlay == OverlayState.Victory) {
                RestartSource.Victory
            } else {
                RestartSource.Playing
            }
            if (game.score == 0L && game.successfulMovesInRun == 0L && game.undo == null) {
                startNewGameBarrier(source)
            } else {
                pendingRestartSource = source
                dispatch(Msg.OverlayChanged(OverlayState.RestartConfirmation))
            }
        }

        private fun confirmRestart() {
            if (!state().acceptsGameInput()) return
            if (state().overlay != OverlayState.RestartConfirmation) return
            startNewGameBarrier(pendingRestartSource ?: RestartSource.Playing)
        }

        private fun newGameFromResult() {
            val current = state()
            if (!current.acceptsGameInput() || current.activeTransition != null) return
            if (current.game?.phase != GamePhase.GameOver) return
            startNewGameBarrier(RestartSource.Result)
        }

        private fun startNewGameBarrier(source: RestartSource) {
            if (destructivePending) return
            val current = state()
            val game = current.game ?: return
            val visibleRevision = current.requestedRevision
            val revision = nextRevision() ?: run {
                publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                publish(TwentyFortyEightStore.Label.TransientError(UiErrorCode.NewGameNotSaved))
                return
            }
            val proposed = try {
                GameRules.restart(
                    RulesState(game, current.statistics),
                    seed = nextSeed(),
                )
            } catch (_: CounterOverflowException) {
                publishRevisionDiagnostic(InvariantCode.CounterOverflow)
                return
            }
            destructivePending = true
            dispatch(Msg.CheckpointRequested(revision))
            scope.launch {
                try {
                    when (val result = coordinator.commitBeforeVisible(commitFor(proposed, revision))) {
                        is CheckpointResult.Stored -> {
                            dispatch(Msg.NewGameCommitted(proposed, result.revision))
                            pendingRestartSource = null
                            publish(TwentyFortyEightStore.Label.NewGameCommitted(proposed.game.runOrdinal))
                            publish(TwentyFortyEightStore.Label.Analytics(AnalyticsFact.Restart(source)))
                            publish(
                                TwentyFortyEightStore.Label.Analytics(
                                    AnalyticsFact.GameStarted(proposed.game.runOrdinal),
                                ),
                            )
                            publish(TwentyFortyEightStore.Label.Audio(AudioEvent.TileSpawn))
                            publishControlsIfChanged(proposed.game)
                            publish(TwentyFortyEightStore.Label.Focus(FocusTarget.Board))
                            publishPendingExternalThrough(result.revision)
                        }
                        is CheckpointResult.Failed -> {
                            dispatch(Msg.BarrierFailed(visibleRevision))
                            publish(TwentyFortyEightStore.Label.Diagnostic(result.failure))
                            publish(TwentyFortyEightStore.Label.TransientError(UiErrorCode.NewGameNotSaved))
                        }
                    }
                } finally {
                    destructivePending = false
                }
            }
        }

        private fun continueAfterVictory() {
            val current = state()
            if (!current.acceptsGameInput() || current.activeTransition != null) return
            val game = current.game ?: return
            val continued = GameRules.continueAfterVictory(RulesState(game, current.statistics))
            if (continued.game == game) return
            if (nextRevision() == null) {
                publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                return
            }
            dispatch(
                Msg.Authoritative(
                    game = continued.game,
                    statistics = continued.statistics,
                    tutorialSeen = current.tutorialSeen,
                    transition = null,
                    overlay = null,
                ),
            )
            publish(TwentyFortyEightStore.Label.Focus(FocusTarget.Board))
            publishControlsIfChanged(continued.game)
            submitCheckpoint(
                listOf(
                    TwentyFortyEightStore.Label.Analytics(
                        AnalyticsFact.Continued(continued.game.runOrdinal),
                    ),
                ),
            )
        }

        private fun skipTutorial() {
            val current = state()
            if (!current.acceptsGameInput() || current.tutorialSeen) return
            val game = current.game ?: return
            if (nextRevision() == null) {
                publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                return
            }
            tutorialReason = TutorialCompletionReason.Skip
            dispatch(
                Msg.Authoritative(
                    game = game,
                    statistics = current.statistics,
                    tutorialSeen = true,
                    transition = current.activeTransition,
                    overlay = current.overlay,
                ),
            )
            submitCheckpoint(
                listOf(
                    TwentyFortyEightStore.Label.Analytics(
                        AnalyticsFact.TutorialCompleted(skipped = true),
                    ),
                ),
            )
        }

        private fun submitCheckpoint(newExternalLabels: List<TwentyFortyEightStore.Label>) {
            val revision = nextRevision() ?: run {
                dispatch(Msg.CheckpointFailed(state().requestedRevision))
                publishRevisionDiagnostic(InvariantCode.RevisionRegression)
                publish(TwentyFortyEightStore.Label.TransientError(UiErrorCode.ProgressNotSaved))
                return
            }
            reserveExternalLabels(revision, newExternalLabels)
            dispatch(Msg.CheckpointRequested(revision))
            val commit = commitFor(
                RulesState(checkNotNull(state().game), state().statistics),
                revision,
            )
            scope.launch {
                coordinator.submit(commit) { result -> handleCheckpointResult(result) }
            }
        }

        private fun handleCheckpointResult(result: CheckpointResult) {
            when (result) {
                is CheckpointResult.Stored -> {
                    dispatch(Msg.CheckpointStored(result.revision))
                    publishPendingExternalThrough(result.revision)
                }
                is CheckpointResult.Failed -> {
                    if (result.revision != state().requestedRevision) return
                    dispatch(Msg.CheckpointFailed(result.revision))
                    publish(TwentyFortyEightStore.Label.Diagnostic(result.failure))
                    publish(TwentyFortyEightStore.Label.TransientError(UiErrorCode.ProgressNotSaved))
                }
            }
        }

        private fun commitFor(rules: RulesState, revision: Long): GameCommit = GameCommit(
            revision = revision,
            game = rules.game,
            bestScore = rules.game.bestScore,
            statistics = rules.statistics,
            tutorialSeen = state().tutorialSeen,
            tutorialReason = tutorialReason,
        )

        private fun publishControlsIfChanged(game: GameState) {
            val controls = AudioControlPolicy.from(game)
            if (controls == lastControls) return
            lastControls = controls
            publish(TwentyFortyEightStore.Label.AudioControlsChanged(controls))
        }

        private fun reserveExternalLabels(
            revision: Long,
            labels: List<TwentyFortyEightStore.Label>,
        ) {
            var droppedOldest = false
            labels.forEach { label ->
                if (pendingExternalLabels.size == MAX_PENDING_EXTERNAL_LABELS) {
                    pendingExternalLabels.removeFirst()
                    droppedOldest = true
                }
                pendingExternalLabels.addLast(PendingExternalLabel(revision, label))
            }
            if (droppedOldest) {
                publishRevisionDiagnostic(InvariantCode.PendingFactOverflow)
            }
        }

        private fun publishPendingExternalThrough(revision: Long) {
            while (pendingExternalLabels.firstOrNull()?.reservationRevision?.let { it <= revision } == true) {
                publish(pendingExternalLabels.removeFirst().label)
            }
        }

        private fun publishRevisionDiagnostic(code: InvariantCode) {
            publish(
                TwentyFortyEightStore.Label.Diagnostic(
                    TwentyFortyEightFailure.InvariantViolation(code),
                ),
            )
        }

        private fun publishResult(game: GameState, statistics: GameStatistics) {
            publish(
                TwentyFortyEightStore.Label.NavigateToResult(
                    ResultSnapshot(
                        score = game.score,
                        bestScore = game.bestScore,
                        highestTile = game.board.values().filterNotNull().maxOrNull() ?: 0L,
                        statistics = statistics,
                    ),
                ),
            )
            publish(TwentyFortyEightStore.Label.Focus(FocusTarget.Result))
        }

        private fun nextRevision(): Long? = state().requestedRevision.takeIf { it < Long.MAX_VALUE }?.plus(1L)

        private fun allocateTransition(): Long? {
            if (nextTransitionId == Long.MAX_VALUE) return null
            return nextTransitionId.also { nextTransitionId += 1L }
        }

        private fun nextSeed(): RngState = RngState.fromBits(seedSource.nextSeed().toULong())
    }

    private fun TwentyFortyEightStore.State.acceptsGameInput(): Boolean =
        bootstrap == BootstrapState.Ready && visibility == MiniAppVisibility.ACTIVE

    private fun TwentyFortyEightStore.Intent.requiresPlayingPhase(): Boolean = when (this) {
        is TwentyFortyEightStore.Intent.Move,
        TwentyFortyEightStore.Intent.Undo,
        TwentyFortyEightStore.Intent.RequestRestart,
        TwentyFortyEightStore.Intent.ConfirmRestart,
        TwentyFortyEightStore.Intent.CancelOverlay,
        TwentyFortyEightStore.Intent.ContinueAfterVictory,
        TwentyFortyEightStore.Intent.SkipTutorial,
        TwentyFortyEightStore.Intent.OpenStatistics,
        -> true
        TwentyFortyEightStore.Intent.NewGameFromResult,
        is TwentyFortyEightStore.Intent.AnimationCompleted,
        is TwentyFortyEightStore.Intent.VisibilityChanged,
        -> false
    }

    private companion object {
        const val FIRST_VICTORY_TRIGGER: String = "twenty_forty_eight_first_victory"
        const val MAX_PENDING_EXTERNAL_LABELS: Int = 64
    }
}

private fun emptyRestoredGameData(): RestoredGameData = RestoredGameData(
    revision = 0L,
    game = null,
    bestScore = 0L,
    statistics = GameStatistics(),
    tutorialSeen = false,
    tutorialReason = null,
    terminal = false,
)
