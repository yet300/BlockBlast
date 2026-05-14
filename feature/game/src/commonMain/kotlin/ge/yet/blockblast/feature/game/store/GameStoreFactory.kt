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
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
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
    fun create(isNewGame: Boolean): GameStore {
        val logger = GameAnalyticsLogger(analytics)
        val initializer = GameInitializer(engine, saveRepository, settings)

        return object :
            GameStore,
            Store<GameStore.Intent, GameStoreState, GameStore.Label> by storeFactory.create(
                name = "GameStore",
                initialState = GameStoreState(
                    game = engine.state.value,
                    continueCountdown = GameStoreState.COUNTDOWN_INACTIVE,
                ),
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameStoreState, GameStore.Msg, GameStore.Label> {
                    onAction<GameStore.Action> {
                        // ── 0. Bootstrap ──────────────────────────────────────────────────
                        // seedBestScore must run before the state collector below, otherwise
                        // the engine's initial bestScore=0 emission could clobber initialState.
                        initializer.seedBestScore()
                        launch {
                            val source = initializer.initialize(isNewGame)
                            logger.log(
                                eventName = "game_started",
                                state = engine.state.value,
                                extra = mapOf("source" to source.tag),
                            )
                        }

                        // ── 1. State snapshots ────────────────────────────────────────────
                        launch {
                            engine.state.collect { dispatch(GameStore.Msg.Snapshot(it)) }
                        }

                        // ── 2. Best-score persistence ─────────────────────────────────────
                        launch {
                            engine.state
                                .map { it.bestScore }
                                .distinctUntilChanged()
                                .collect { best ->
                                    if (best > settings.bestScore.value) settings.setBestScore(best)
                                }
                        }

                        // ── 3. Game-over edge → countdown + (one-shot) review ─────────────
                        // bestAtRoundStart / reviewPromptFiredThisRound live on GameState so
                        // the qualifier survives store recreation across Home → Play. The
                        // executor stays stateless wrt those flags.
                        val countdown = ReviveCountdownManager(
                            onTick = { secs -> dispatch(GameStore.Msg.CountdownTick(secs)) },
                            onExpired = {
                                logger.log(
                                    eventName = "revive_countdown_expired",
                                    state = engine.state.value,
                                    extra = mapOf("countdown_seconds" to 0),
                                )
                            },
                        )
                        launch {
                            var previousIsGameOver = engine.state.value.isGameOver
                            engine.state
                                .map { it.isGameOver }
                                .collect { isGameOver ->
                                    if (isGameOver == previousIsGameOver) return@collect
                                    previousIsGameOver = isGameOver
                                    val gameState = engine.state.value
                                    if (isGameOver) {
                                        logger.log("game_over", gameState)
                                        if (qualifiesForReview(gameState)) {
                                            engine.markReviewPromptFired()
                                            launch { settings.incrementReviewPromptCount() }
                                            publish(GameStore.Label.RequestReview)
                                        }
                                        countdown.start(this)
                                    } else {
                                        countdown.cancel()
                                    }
                                }
                        }

                        // ── 4a. SFX/voice: edge-triggered from engine events ──────────────
                        launch {
                            engine.events.collect { event ->
                                when (event) {
                                    is GameEvent.PiecePlaced -> audio.playPlacementSound()
                                    is GameEvent.LinesCleared -> {
                                        audio.playClearSound(event.linesCount)
                                        logger.log(
                                            eventName = "lines_cleared",
                                            state = engine.state.value,
                                            extra = mapOf(
                                                "lines_count" to event.linesCount,
                                                "is_cross_clear" to event.isCrossClear,
                                            ),
                                        )
                                    }
                                    is GameEvent.Feedback -> audio.playVoiceFeedback(event.type)
                                    is GameEvent.ComboActive -> {
                                        audio.playVoiceCombo(event.level)
                                        logger.log(
                                            eventName = "combo_reached",
                                            state = engine.state.value,
                                            extra = mapOf("combo_level" to event.level),
                                        )
                                    }
                                    is GameEvent.GameOver,
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
                            "x" to intent.x,
                            "y" to intent.y,
                            "remaining_pieces" to before.currentPieces.size,
                        )
                        logger.log("piece_place_attempt", before, placementParams)
                        val placed = engine.placePiece(intent.pieceId, intent.x, intent.y)
                        logger.log(
                            eventName = if (placed) "piece_place_success" else "piece_place_failed",
                            state = engine.state.value,
                            extra = placementParams,
                        )
                    }
                    onIntent<GameStore.Intent.Revive> {
                        logger.log("revive_clicked", engine.state.value)
                        launch {
                            if (engine.continueWithSmallBlocks()) {
                                val state = engine.state.value
                                logger.log("revive_completed", state, mapOf("source" to "revive"))
                                logger.log("game_started", state, mapOf("source" to "revive"))
                            }
                        }
                    }
                    onIntent<GameStore.Intent.Restart> {
                        logger.log("restart_clicked", engine.state.value)
                        launch {
                            engine.startNewGame(bestScore = engine.state.value.bestScore)
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
            is GameStore.Msg.CountdownTick -> copy(continueCountdown = msg.secondsRemaining)
        }
    }

    private fun qualifiesForReview(state: ge.yet.blokblast.domain.model.GameState): Boolean {
        val beatBy = state.score - state.bestAtRoundStart
        return !state.reviewPromptFiredThisRound &&
            state.score >= AppConfig.REVIEW_MIN_SCORE &&
            beatBy >= AppConfig.REVIEW_BEST_SCORE_DELTA &&
            settings.reviewPromptCount.value < AppConfig.REVIEW_MAX_PROMPTS
    }
}
