package ge.yet.blockblast.feature.game.store

import com.app.common.config.AppConfig
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.model.GameEvent
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.model.Grid
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
internal class GameStoreFactory(
    private val storeFactory: StoreFactory,
    private val engine: GameEngine,
    private val audio: AudioRepository,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
) {
    fun create(
        isNewGame: Boolean,
        restoredResultState: GameState? = null,
    ): GameStore {
        val logger = GameAnalyticsLogger(analytics)
        val initializer = GameInitializer(engine, saveRepository, settings)
        restoredResultState?.let(engine::restoreResult)

        return object :
            GameStore,
            Store<GameStore.Intent, GameStoreState, GameStore.Label> by storeFactory.create(
                name = "GameStore",
                initialState = GameStoreState(game = engine.state.value),
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameStoreState, GameStore.Msg, GameStore.Label> {
                    var rollbackStateToSuppress: GameState? = null

                    onAction<GameStore.Action> {
                        // ── 0. Bootstrap ──────────────────────────────────────────────────
                        // seedBestScore must run before the state collector below, otherwise
                        // the engine's initial bestScore=0 emission could clobber initialState.
                        if (restoredResultState == null) {
                            initializer.seedBestScore()
                        }
                        launch {
                            val source = initializer.initialize(
                                isNewGame = isNewGame,
                                restoredResultState = restoredResultState,
                            )
                            if (source != GameInitializer.Source.ResultRestore) {
                                logger.log(
                                    eventName = "game_started",
                                    state = engine.state.value,
                                    extra = mapOf("source" to source.tag),
                                )
                            }
                        }

                        // ── 1. State snapshots ────────────────────────────────────────────
                        launch {
                            engine.state.collect { dispatch(GameStore.Msg.Snapshot(it)) }
                        }

                        // ── 2. Best-score persistence ─────────────────────────────────────
                        // setBestScore is monotonic at the repo level — no caller-side guard.
                        launch {
                            engine.state
                                .map { it.bestScore }
                                .distinctUntilChanged()
                                .collect { best ->
                                    attemptPersistence(
                                        logger = logger,
                                        operation = "best_score",
                                        state = engine.state.value,
                                    ) {
                                        settings.setBestScore(best)
                                    }
                                }
                        }

                        // ── 3. Game-over edge → persisted Result ──────────────────────────
                        launch {
                            var previousIsGameOver = engine.state.value.isGameOver
                            engine.state.collect { gameState ->
                                val isGameOver = gameState.isGameOver
                                val rollbackState = rollbackStateToSuppress
                                if (rollbackState != null &&
                                    isGameOver &&
                                    gameState == rollbackState
                                ) {
                                    rollbackStateToSuppress = null
                                    previousIsGameOver = true
                                    return@collect
                                }
                                if (isGameOver == previousIsGameOver) return@collect
                                previousIsGameOver = isGameOver
                                if (isGameOver) {
                                    logger.log("game_over", gameState)
                                    val shouldRequestReview =
                                        qualifiesForReview(gameState) &&
                                            attemptPersistence(
                                                logger = logger,
                                                operation = "review_prompt_count",
                                                state = gameState,
                                            ) {
                                                settings.incrementReviewPromptCount()
                                            }
                                    if (shouldRequestReview) {
                                        engine.markReviewPromptFired()
                                    }
                                    val markedState =
                                        if (shouldRequestReview) engine.state.value else gameState
                                    val finalState = markedState.copy(
                                        grid = Grid(markedState.grid.cells.copyOf()),
                                    )
                                    // Result navigation must never race the final save.
                                    attemptPersistence(
                                        logger = logger,
                                        operation = "terminal_save",
                                        state = finalState,
                                    ) {
                                        engine.saveNow(finalState)
                                    }
                                    attemptPersistence(
                                        logger = logger,
                                        operation = "terminal_best_score",
                                        state = finalState,
                                    ) {
                                        settings.setBestScore(finalState.bestScore)
                                    }
                                    publish(
                                        GameStore.Label.GameCompleted(
                                            finalState = finalState,
                                            canContinue = finalState.revivesUsed < GameState.MAX_REVIVES,
                                            shouldRequestReview = shouldRequestReview,
                                        ),
                                    )
                                }
                            }
                        }

                        // ── 4a. SFX/voice: edge-triggered from engine events ──────────────
                        launch {
                            engine.events.collect { event ->
                                when (event) {
                                    is GameEvent.MoveResolved -> {
                                        val moveParams = moveAnalyticsParams(event)
                                        audio.playPlacementSound()
                                        if (event.linesCount > 0) {
                                            audio.playClearSound(event.linesCount)
                                        }
                                        event.feedback?.let { audio.playVoiceFeedback(it) }

                                        logger.log(
                                            eventName = "piece_place_success",
                                            state = engine.state.value,
                                            extra = moveParams,
                                        )
                                        if (event.linesCount > 0) {
                                            logger.log(
                                                eventName = "lines_cleared",
                                                state = engine.state.value,
                                                extra = moveParams,
                                            )
                                        }
                                        if (event.linesCount > 0 && event.comboLevel >= 2) {
                                            logger.log(
                                                eventName = "combo_reached",
                                                state = engine.state.value,
                                                extra = moveParams,
                                            )
                                        }
                                    }
                                    is GameEvent.GameStarted -> Unit
                                }
                            }
                        }

                        // ── 4b. Music: derived from continuous state, not events ──────────
                        // Driving music from events would miss the first GameStarted on cold
                        // launch (SharedFlow replay=0, bootstrap may emit before this
                        // collector subscribes). State-derived is idempotent through
                        // distinctUntilChanged.
                        launch {
                            engine.state
                                .map { !it.isGameOver && it.currentPieces.isNotEmpty() }
                                .distinctUntilChanged()
                                .collect { shouldPlay ->
                                    if (shouldPlay) audio.startMusic() else audio.stopMusic()
                                }
                        }
                    }
                    onIntent<GameStore.Intent.Place> { intent ->
                        val before = engine.state.value
                        val placementParams = mapOf(
                            "piece_id" to intent.pieceId,
                            "remaining_pieces" to before.currentPieces.size,
                        )
                        logger.log("piece_place_attempt", before, placementParams)
                        val placed = engine.placePiece(intent.pieceId, intent.x, intent.y)
                        if (!placed) {
                            logger.log(
                                eventName = "piece_place_failed",
                                state = engine.state.value,
                                extra = placementParams,
                            )
                        }
                    }
                    onIntent<GameStore.Intent.Revive> {
                        rollbackStateToSuppress = null
                        val terminalState = engine.state.value.copy(
                            grid = Grid(engine.state.value.grid.cells.copyOf()),
                        )
                        logger.log("revive_clicked", terminalState)
                        val revived = engine.continueWithSmallBlocks()
                        val state = engine.state.value
                        dispatch(GameStore.Msg.Snapshot(state))
                        if (revived) {
                            logger.log("revive_completed", state, mapOf("source" to "revive"))
                            logger.log("game_started", state, mapOf("source" to "revive"))
                            val playableState = state.copy(
                                grid = Grid(state.grid.cells.copyOf()),
                            )
                            launch {
                                val saved = attemptPersistence(
                                    logger = logger,
                                    operation = "revive_save",
                                    state = playableState,
                                ) {
                                    engine.saveNow(playableState)
                                }
                                if (saved) {
                                    publish(GameStore.Label.ReviveCompleted(playableState))
                                } else {
                                    engine.restoreResult(terminalState)
                                    rollbackStateToSuppress = engine.state.value
                                    dispatch(GameStore.Msg.Snapshot(engine.state.value))
                                    publish(GameStore.Label.ReviveFailed)
                                }
                            }
                        } else {
                            publish(GameStore.Label.ReviveFailed)
                        }
                    }
                    onIntent<GameStore.Intent.Restart> {
                        logger.log("restart_clicked", engine.state.value)
                        launch {
                            engine.startNewGame(
                                bestScore = engine.state.value.bestScore,
                                allowStarterLayout = settings.tutorialSeen.value,
                            )
                            logger.log(
                                eventName = "game_started",
                                state = engine.state.value,
                                extra = mapOf("source" to "restart"),
                            )
                        }
                    }
                },
                reducer = GameReducer,
                bootstrapper = SimpleBootstrapper(GameStore.Action.Init),
            ) {}
    }

    internal object GameReducer : Reducer<GameStoreState, GameStore.Msg> {
        override fun GameStoreState.reduce(msg: GameStore.Msg): GameStoreState = when (msg) {
            is GameStore.Msg.Snapshot -> copy(game = msg.state)
        }
    }

    private suspend fun attemptPersistence(
        logger: GameAnalyticsLogger,
        operation: String,
        state: GameState,
        block: suspend () -> Unit,
    ): Boolean =
        try {
            block()
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logger.log(
                eventName = "game_persistence_failed",
                state = state,
                extra = mapOf(
                    "operation" to operation,
                    "error_type" to (error::class.simpleName ?: "Exception"),
                ),
            )
            false
        }

    private fun moveAnalyticsParams(event: GameEvent.MoveResolved): Map<String, Any> = mapOf(
        "piece_id" to event.pieceId,
        "placed_cells" to event.placedCellCount,
        "lines_count" to event.linesCount,
        "cleared_cells" to event.clearedCells.size,
        "is_cross_clear" to event.isCrossClear,
        "is_all_clear" to event.isBoardEmpty,
        "placement_points" to event.placementPoints,
        "clear_points" to event.clearPoints,
        "all_clear_points" to event.allClearPoints,
        "total_points" to event.totalPoints,
        "combo_level" to event.comboLevel,
        "moves_without_clear" to event.movesWithoutClear,
        "feedback" to (event.feedback?.name?.lowercase() ?: "none"),
        "is_game_over" to event.isGameOver,
    )

    private fun qualifiesForReview(state: GameState): Boolean {
        val beatBy = state.score - state.bestAtRoundStart
        return !state.reviewPromptFiredThisRound &&
            state.score >= AppConfig.REVIEW_MIN_SCORE.toLong() &&
            beatBy >= AppConfig.REVIEW_BEST_SCORE_DELTA &&
            settings.reviewPromptCount.value < AppConfig.REVIEW_MAX_PROMPTS
    }
}
