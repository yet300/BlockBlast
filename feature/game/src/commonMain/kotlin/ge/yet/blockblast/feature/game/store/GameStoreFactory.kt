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
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.GameSaveRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import ge.yet.blokblast.domain.repository.StoreReviewRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@Inject
internal class GameStoreFactory(
    private val storeFactory: StoreFactory,
    private val engine: GameEngine,
    private val audio: AudioRepository,
    private val storeReview: StoreReviewRepository,
    private val saveRepository: GameSaveRepository,
    private val settings: SettingsRepository,
    private val analytics: AnalyticRepository,
) {
    fun create(isNewGame: Boolean): GameStore =
        object :
            GameStore,
            Store<GameStore.Intent, GameStoreState, GameStore.Label> by storeFactory.create(
                name = "GameStore",
                initialState = GameStoreState(
                    game = engine.state.value,
                    continueCountdown = GameStoreState.COUNTDOWN_INACTIVE,
                ),
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameStoreState, GameStore.Msg, GameStore.Label> {
                    onAction<GameStore.Action> {
                        // ── 0. Bootstrap: seed best, attempt save-restore ─────────────────
                        // GameEngine starts at bestScore = 0; without seeding it, the
                        // first `engine.state` emission (section 1) would overwrite our
                        // initialState with that 0, and `max(currentBest, score)` in
                        // placePiece would collapse "Best" onto the current round's
                        // score. seedBestScore is a no-op if the engine already knows a
                        // higher value (e.g. carried over from an earlier session in
                        // the same process), so it is safe to run on every bootstrap.
                        engine.seedBestScore(settings.bestScore.value)

                        // Save-restore / Initialization:
                        // We consolidate this here to avoid race conditions between
                        // cold-start restoration and the Decompose-triggered Intent.Start.
                        launch {
                            val current = engine.state.value
                            // 1. New game requested OR existing game in memory is already over
                            if (isNewGame || current.isGameOver) {
                                engine.startNewGame(bestScore = current.bestScore)
                                logGameEvent("game_started", extra = mapOf("source" to "new"))
                            }
                            // 2. "Continue" requested AND engine is empty (Cold Start)
                            else if (current.currentPieces.isEmpty()) {
                                val saved = saveRepository.load()
                                if (saved != null && !saved.isGameOver && saved.currentPieces.isNotEmpty()) {
                                    engine.restore(saved)
                                    logGameEvent("game_started", extra = mapOf("source" to "continue"))
                                } else {
                                    // Nothing to continue, start fresh
                                    engine.startNewGame(bestScore = current.bestScore)
                                    logGameEvent("game_started", extra = mapOf("source" to "new"))
                                }
                            }
                            // 3. "Continue" requested AND engine already has state (Warm Start)
                            // -> Do nothing, the state-snapshot collector will pick up current state.
                            else {
                                logGameEvent("game_started", extra = mapOf("source" to "continue"))
                            }
                        }

                        // ── 1. State snapshots ────────────────────────────────────────────
                        // StateFlow already deduplicates by structural equality. Grid uses
                        // IntArray with contentEquals-based equals, so equal states compare
                        // equal and no-op emissions are suppressed upstream.
                        launch {
                            engine.state.collect { gameState ->
                                dispatch(GameStore.Msg.Snapshot(gameState))
                            }
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
                        // Edge-triggered: only react when isGameOver actually flips.
                        // We seed `previous` from the current engine value so the initial
                        // StateFlow replay (= true if the user re-enters during game-over)
                        // does not look like a fresh transition and re-fire the countdown.
                        //
                        // The "is this a new personal best worth prompting for review?"
                        // qualifier and the "did we already prompt this round?" flag both
                        // live on GameState now (bestAtRoundStart, reviewPromptFiredThisRound)
                        // so they survive store recreation across Home → Play. Engine-state
                        // is the source of truth — the executor is stateless wrt these.
                        launch {
                            var countdownJob: Job? = null
                            var previousIsGameOver = engine.state.value.isGameOver

                            engine.state
                                .map { it.isGameOver }
                                .collect { isGameOver ->
                                    if (isGameOver == previousIsGameOver) return@collect
                                    previousIsGameOver = isGameOver
                                    val gameState = engine.state.value
                                    if (isGameOver) {
                                        logGameEvent("game_over", state = gameState)
                                        val score = gameState.score
                                        val beatBy = score - gameState.bestAtRoundStart
                                        val qualifies =
                                            !gameState.reviewPromptFiredThisRound &&
                                                score >= AppConfig.REVIEW_MIN_SCORE &&
                                                beatBy >= AppConfig.REVIEW_BEST_SCORE_DELTA &&
                                                settings.reviewPromptCount.value <
                                                AppConfig.REVIEW_MAX_PROMPTS
                                        if (qualifies) {
                                            engine.markReviewPromptFired()
                                            launch { settings.incrementReviewPromptCount() }
                                            // Hand the actual prompt to the component via a
                                            // Label so navigation/SDK calls don't live in
                                            // the executor. Per the mvikotlin-code skill.
                                            publish(GameStore.Label.RequestReview)
                                        }
                                        countdownJob?.cancel()
                                        countdownJob = launch {
                                            var seconds = GameStoreState.CONTINUE_COUNTDOWN_SECONDS
                                            dispatch(GameStore.Msg.CountdownTick(seconds))
                                            while (seconds > 0) {
                                                delay(1000)
                                                seconds -= 1
                                                dispatch(GameStore.Msg.CountdownTick(seconds))
                                            }
                                            logGameEvent(
                                                eventName = "revive_countdown_expired",
                                                extra = mapOf("countdown_seconds" to 0),
                                            )
                                        }
                                    } else {
                                        countdownJob?.cancel()
                                        countdownJob = null
                                        dispatch(
                                            GameStore.Msg.CountdownTick(GameStoreState.COUNTDOWN_INACTIVE),
                                        )
                                    }
                                }
                        }

                        // ── 4a. SFX/voice: edge-triggered from engine events ──────────────
                        // Per-placement sounds and clear-line voice lines fire on
                        // discrete events. These collectors only see emissions made
                        // after they subscribe — fine, because every placement
                        // happens long after bootstrap.
                        launch {
                            engine.events.collect { event ->
                                when (event) {
                                    is GameEvent.PiecePlaced -> audio.playPlacementSound()
                                    is GameEvent.LinesCleared -> {
                                        audio.playClearSound(event.linesCount)
                                        logGameEvent(
                                            eventName = "lines_cleared",
                                            extra = mapOf(
                                                "lines_count" to event.linesCount,
                                                "is_cross_clear" to event.isCrossClear,
                                            ),
                                        )
                                    }
                                    is GameEvent.Feedback -> audio.playVoiceFeedback(event.type)
                                    is GameEvent.ComboActive -> {
                                        audio.playVoiceCombo(event.level)
                                        logGameEvent(
                                            eventName = "combo_reached",
                                            extra = mapOf("combo_level" to event.level),
                                        )
                                    }
                                    // Music is *not* driven from events — see 4b.
                                    is GameEvent.GameOver,
                                    is GameEvent.GameStarted -> Unit
                                }
                            }
                        }

                        // ── 4b. Music: derived from continuous state, not events ──────────
                        // The previous implementation reacted to GameStarted/GameOver
                        // edges. That misses the *first* GameStarted on cold launch:
                        // engine.events is a SharedFlow with replay = 0, and the
                        // bootstrap's save-restore launch may emit before this
                        // coroutine has actually subscribed → event lost → music
                        // never starts. Music is a continuous "is a round in flight?"
                        // signal, so derive it from state instead. Idempotent: the
                        // same `shouldPlay = true` emission collapses through
                        // distinctUntilChanged, and the audio repository de-dupes
                        // start/stop at the player level too.
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
                        logGameEvent(
                            eventName = "piece_place_attempt",
                            state = before,
                            extra = placementParams,
                        )
                        val placed = engine.placePiece(intent.pieceId, intent.x, intent.y)
                        logGameEvent(
                            eventName = if (placed) "piece_place_success" else "piece_place_failed",
                            state = engine.state.value,
                            extra = placementParams,
                        )
                    }
                    onIntent<GameStore.Intent.Revive> {
                        logGameEvent("revive_clicked")
                        launch {
                            if (engine.continueWithSmallBlocks()) {
                                logGameEvent(
                                    eventName = "revive_completed",
                                    extra = mapOf("source" to "revive"),
                                )
                                logGameEvent(
                                    eventName = "game_started",
                                    extra = mapOf("source" to "revive"),
                                )
                            }
                        }
                    }
                    onIntent<GameStore.Intent.Restart> {
                        logGameEvent("restart_clicked")
                        launch {
                            engine.startNewGame(bestScore = engine.state.value.bestScore)
                            logGameEvent(
                                eventName = "game_started",
                                extra = mapOf("source" to "restart"),
                            )
                        }
                    }
                },
                reducer = GameReducer,
                bootstrapper = SimpleBootstrapper(GameStore.Action.Init),
            ) {}

    private fun logGameEvent(
        eventName: String,
        state: GameState = engine.state.value,
        extra: Map<String, Any> = emptyMap(),
    ) {
        analytics.logEvent(
            eventName = eventName,
            params = gameParams(state) + extra,
        )
    }

    private fun gameParams(state: GameState): Map<String, Any> =
        mapOf(
            "score" to state.score,
            "best_score" to state.bestScore,
            "revives_used" to state.revivesUsed,
            "remaining_pieces" to state.currentPieces.size,
        )

    internal object GameReducer : Reducer<GameStoreState, GameStore.Msg> {
        override fun GameStoreState.reduce(msg: GameStore.Msg): GameStoreState = when (msg) {
            is GameStore.Msg.Snapshot -> copy(game = msg.state)
            is GameStore.Msg.CountdownTick -> copy(continueCountdown = msg.secondsRemaining)
        }
    }
}
