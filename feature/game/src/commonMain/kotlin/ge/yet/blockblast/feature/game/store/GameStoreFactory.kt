package ge.yet.blockblast.feature.game.store

import com.app.common.config.AppConfig
import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.CoroutineExecutor
import dev.zacsweers.metro.Inject
import ge.yet.game.domain.engine.GameSessionReducer
import ge.yet.game.domain.engine.GameTransition
import ge.yet.game.domain.model.GameEvent
import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.RoundStartInfo
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.GameSaveRepository
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Inject
internal class GameStoreFactory(
    private val storeFactory: StoreFactory,
    private val gameReducer: GameSessionReducer,
    private val audio: AudioRepository,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
) {
    fun create(
        isNewGame: Boolean,
        restoredResultState: GameState? = null,
        newGameSeed: Long? = null,
    ): GameStore {
        val logger = GameAnalyticsLogger(analytics)
        val initializer = GameInitializer(gameReducer, saveRepository, settings)
        val initialState = restoredResultState?.let(gameReducer::restoreResult) ?: GameState(
            bestScore = settings.bestScore.value,
            bestAtRoundStart = settings.bestScore.value,
        )
        return object : GameStore,
            Store<GameStore.Intent, GameState, GameStore.Label> by storeFactory.create(
                name = "GameStore",
                initialState = initialState,
                executorFactory = {
                    ExecutorImpl(
                        isNewGame = isNewGame,
                        restoredResultState = restoredResultState,
                        newGameSeed = newGameSeed,
                        initializer = initializer,
                        logger = logger,
                    )
                },
                reducer = GameReducer,
                bootstrapper = SimpleBootstrapper(GameStore.Action.Init),
            ) {}
    }

    private inner class ExecutorImpl(
        private val isNewGame: Boolean,
        private val restoredResultState: GameState?,
        private val newGameSeed: Long?,
        private val initializer: GameInitializer,
        private val logger: GameAnalyticsLogger,
    ) : CoroutineExecutor<GameStore.Intent, GameStore.Action, GameState, GameStore.Msg, GameStore.Label>() {
        private val saveCoordinator = GameSaveCoordinator(saveRepository)

        override fun executeAction(action: GameStore.Action) {
            when (action) {
                GameStore.Action.Init -> initialize()
            }
        }

        override fun executeIntent(intent: GameStore.Intent) {
            when (intent) {
                is GameStore.Intent.Place -> place(intent)
                GameStore.Intent.Revive -> revive()
                GameStore.Intent.Restart -> restart()
            }
        }

        private fun initialize() {
            scope.launch {
                val initialization = initializer.initialize(
                    isNewGame = isNewGame,
                    restoredResultState = restoredResultState,
                    newGameSeed = newGameSeed,
                )
                dispatch(GameStore.Msg.Snapshot(initialization.state))
                updateMusic(initialization.state)
                if (initialization.source != GameInitializer.Source.ResultRestore) {
                    logger.log(
                        eventName = "game_started",
                        state = initialization.state,
                        extra = mapOf("source" to initialization.source.tag) +
                            initialization.roundStart.orEmptyAnalytics(initialization.state),
                    )
                }
                if (initialization.source == GameInitializer.Source.New) {
                    scheduleSave(initialization.state)
                }
            }
        }

        private fun place(intent: GameStore.Intent.Place) {
            val before = state()
            val placementParams = mapOf(
                "piece_id" to intent.pieceId,
                "remaining_pieces" to before.currentPieces.size,
            )
            logger.log("piece_place_attempt", before, placementParams)
            when (val transition = gameReducer.place(before, intent.pieceId, intent.x, intent.y)) {
                is GameTransition.Rejected -> logger.log(
                    eventName = "piece_place_failed",
                    state = before,
                    extra = placementParams + ("reason" to transition.reason.name),
                )

                is GameTransition.Applied -> {
                    dispatch(GameStore.Msg.Snapshot(transition.state))
                    scheduleSave(transition.state)
                    val event = transition.fact as? GameEvent.MoveResolved ?: return
                    scope.launch { processMove(before, transition.state, event) }
                }
            }
        }

        private suspend fun processMove(
            before: GameState,
            state: GameState,
            event: GameEvent.MoveResolved,
        ) {
            val moveParams = moveAnalyticsParams(event)
            event.feedback?.let { audio.playVoiceFeedback(it) }
            logger.log("piece_place_success", state, moveParams)
            if (event.linesCount > 0) logger.log("lines_cleared", state, moveParams)
            if (event.linesCount > 0 && event.comboLevel >= 2) {
                logger.log("combo_reached", state, moveParams)
            }
            if (state.bestScore != before.bestScore) {
                attemptPersistence(logger, "best_score", state) {
                    settings.setBestScore(state.bestScore)
                }
            }
            if (event.isGameOver) {
                audio.stopMusic()
                completeGame(state)
            }
        }

        private suspend fun completeGame(gameState: GameState) {
            logger.log("game_over", gameState)
            val shouldRequestReview = qualifiesForReview(gameState) &&
                attemptPersistence(logger, "review_prompt_count", gameState) {
                    settings.incrementReviewPromptCount()
                }
            val markedState = if (shouldRequestReview) {
                gameReducer.markReviewPromptFired(gameState).also {
                    dispatch(GameStore.Msg.Snapshot(it))
                }
            } else {
                gameState
            }
            val finalState = markedState.copy(grid = Grid(markedState.grid.cells.copyOf()))
            attemptPersistence(logger, "terminal_save", finalState) {
                saveCoordinator.flush(finalState)
            }
            attemptPersistence(logger, "terminal_best_score", finalState) {
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

        private fun revive() {
            val current = state()
            val terminalState = current.copy(grid = Grid(current.grid.cells.copyOf()))
            logger.log("revive_clicked", terminalState)
            when (val transition = gameReducer.revive(terminalState)) {
                is GameTransition.Rejected -> publish(GameStore.Label.ReviveFailed)
                is GameTransition.Applied -> {
                    val playableState = transition.state
                    dispatch(GameStore.Msg.Snapshot(playableState))
                    scope.launch {
                        updateMusic(playableState)
                        logger.log("revive_completed", playableState, mapOf("source" to "revive"))
                        logger.log("game_started", playableState, mapOf("source" to "revive"))
                        val saved = attemptPersistence(logger, "revive_save", playableState) {
                            saveCoordinator.flush(playableState)
                        }
                        if (saved) {
                            publish(GameStore.Label.ReviveCompleted(playableState))
                        } else {
                            dispatch(GameStore.Msg.Snapshot(terminalState))
                            updateMusic(terminalState)
                            publish(GameStore.Label.ReviveFailed)
                        }
                    }
                }
            }
        }

        private fun restart() {
            val before = state()
            logger.log("restart_clicked", before)
            val roundStart = gameReducer.startNewGame(
                previousState = before,
                bestScore = before.bestScore,
                allowStarterLayout = settings.tutorialSeen.value,
            )
            dispatch(GameStore.Msg.Snapshot(roundStart.state))
            scheduleSave(roundStart.state)
            scope.launch {
                updateMusic(roundStart.state)
                logger.log(
                    eventName = "game_started",
                    state = roundStart.state,
                    extra = mapOf("source" to "restart") +
                        roundStartAnalyticsParams(roundStart.info, roundStart.state),
                )
            }
        }

        private fun scheduleSave(snapshot: GameState) {
            saveCoordinator.schedule(
                scope = scope,
                state = snapshot,
                onFailure = { error ->
                    logPersistenceFailure(logger, "autosave", snapshot, error)
                },
            )
        }

        private suspend fun updateMusic(snapshot: GameState) {
            if (!snapshot.isGameOver && snapshot.currentPieces.isNotEmpty()) {
                audio.startMusic()
            } else {
                audio.stopMusic()
            }
        }
    }

    internal object GameReducer : Reducer<GameState, GameStore.Msg> {
        override fun GameState.reduce(msg: GameStore.Msg): GameState = when (msg) {
            is GameStore.Msg.Snapshot -> msg.state
        }
    }

    private suspend fun attemptPersistence(
        logger: GameAnalyticsLogger,
        operation: String,
        state: GameState,
        block: suspend () -> Unit,
    ): Boolean = try {
        block()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        logPersistenceFailure(logger, operation, state, error)
        false
    }

    private fun logPersistenceFailure(
        logger: GameAnalyticsLogger,
        operation: String,
        state: GameState,
        error: Exception,
    ) {
        logger.log(
            eventName = "game_persistence_failed",
            state = state,
            extra = mapOf(
                "operation" to operation,
                "error_type" to (error::class.simpleName ?: "Exception"),
            ),
        )
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

    private fun RoundStartInfo?.orEmptyAnalytics(state: GameState): Map<String, Any> =
        this?.let { roundStartAnalyticsParams(it, state) }.orEmpty()

    private fun roundStartAnalyticsParams(info: RoundStartInfo, state: GameState): Map<String, Any> =
        buildMap {
            put("layout_source", info.layoutSource.name.lowercase())
            put("initial_occupied_cells", state.grid.cells.count { it != Grid.EMPTY })
            put("initial_tray_shape_ids", state.currentPieces.joinToString(",") { it.shape.id })
            put(
                "initial_tray_size_categories",
                state.currentPieces.joinToString(",") { piece ->
                    when (piece.shape.size) {
                        in 1..2 -> "compact"
                        in 3..4 -> "medium"
                        else -> "large"
                    }
                },
            )
            info.starterTemplateId?.let { put("starter_template_id", it) }
            info.quarterTurns?.let { put("starter_quarter_turns", it) }
            info.reflectedHorizontally?.let { put("starter_reflected_horizontally", it) }
        }

    private fun qualifiesForReview(state: GameState): Boolean {
        val beatBy = state.score - state.bestAtRoundStart
        return !state.reviewPromptFiredThisRound &&
            state.score >= AppConfig.REVIEW_MIN_SCORE.toLong() &&
            beatBy >= AppConfig.REVIEW_BEST_SCORE_DELTA &&
            settings.reviewPromptCount.value < AppConfig.REVIEW_MAX_PROMPTS
    }
}
