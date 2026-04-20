package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.app.common.config.AppConfig
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.model.GameEvent
import ge.yet.blokblast.domain.repository.AudioRepository
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
    private val settings: SettingsRepository,
) {
    fun create(): GameStore =
        object :
            GameStore,
            Store<GameStore.Intent, GameStoreState, Nothing> by storeFactory.create(
                name = "GameStore",
                initialState = GameStoreState(
                    // Seed in-engine best score from persisted settings so a
                    // fresh process knows the player's lifetime best before any
                    // game has finished this session.
                    game = engine.state.value.copy(bestScore = settings.bestScore.value),
                    continueCountdown = GameStoreState.COUNTDOWN_INACTIVE,
                ),
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameStoreState, GameStore.Msg, Nothing> {
                    onAction<GameStore.Action> {
                        // ── 1. State snapshots ────────────────────────────────────────────
                        // StateFlow already deduplicates by structural equality, so no
                        // distinctUntilChanged() needed here. Grid now uses IntArray
                        // with contentEquals-based equals — equal states compare equal,
                        // so no-op emissions are suppressed upstream.
                        launch {
                            engine.state.collect { gameState ->
                                dispatch(GameStore.Msg.Snapshot(gameState))
                            }
                        }

                        // ── 2. Best-score persistence ─────────────────────────────────────
                        // Separate coroutine so unrelated state changes (grid, pieces)
                        // don't trigger a disk write. distinctUntilChanged ensures we
                        // only act when bestScore actually increases.
                        launch {
                            engine.state
                                .map { it.bestScore }
                                .distinctUntilChanged()
                                .collect { best ->
                                    if (best > settings.bestScore.value) settings.setBestScore(best)
                                }
                        }

                        // ── 3. Game-over transitions ──────────────────────────────────────
                        // distinctUntilChanged on isGameOver gives us one emission per
                        // edge (false→true and true→false) with no manual wasGameOver var.
                        // engine.state is a StateFlow so .value is always current.
                        launch {
                            var countdownJob: Job? = null
                            // Track the best score at the start of each round so we can
                            // detect a genuine new personal best at game-over time.
                            // Use the persisted best as baseline for fresh processes.
                            var bestBeforeGameOver = maxOf(
                                engine.state.value.bestScore,
                                settings.bestScore.value,
                            )
                            var reviewRequested = false

                            engine.state
                                .map { it.isGameOver }
                                .distinctUntilChanged()
                                .collect { isGameOver ->
                                    val gameState = engine.state.value
                                    if (isGameOver) {
                                        // false → true: start countdown & maybe trigger review.
                                        // In-app review is intentionally rare:
                                        //   1. score must clear REVIEW_MIN_SCORE
                                        //   2. score must beat the previous best
                                        //      by at least REVIEW_BEST_SCORE_DELTA
                                        //   3. lifetime prompt count must still be
                                        //      below REVIEW_MAX_PROMPTS
                                        val score = gameState.score
                                        val beatBy = score - bestBeforeGameOver
                                        val qualifies =
                                            score >= AppConfig.REVIEW_MIN_SCORE &&
                                                beatBy >= AppConfig.REVIEW_BEST_SCORE_DELTA &&
                                                settings.reviewPromptCount.value <
                                                AppConfig.REVIEW_MAX_PROMPTS
                                        if (!reviewRequested && qualifies) {
                                            reviewRequested = true
                                            launch {
                                                settings.incrementReviewPromptCount()
                                                storeReview.requestInAppReview().collect {}
                                            }
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
                                        }
                                    } else {
                                        // true → false: new round — arm review trigger again
                                        // and snapshot the best score to beat next time.
                                        countdownJob?.cancel()
                                        countdownJob = null
                                        reviewRequested = false
                                        bestBeforeGameOver = gameState.bestScore
                                        dispatch(
                                            GameStore.Msg.CountdownTick(GameStoreState.COUNTDOWN_INACTIVE),
                                        )
                                    }
                                }
                        }

                        // ── 4. Audio events ───────────────────────────────────────────────
                        launch {
                            engine.events.collect { event ->
                                when (event) {
                                    is GameEvent.PiecePlaced -> audio.playPlacementSound()
                                    is GameEvent.LinesCleared -> audio.playClearSound(event.linesCount)
                                    is GameEvent.Feedback -> audio.playVoiceFeedback(event.type)
                                    is GameEvent.ComboActive -> audio.playVoiceCombo(event.level)
                                    is GameEvent.GameOver -> audio.stopMusic()
                                }
                            }
                        }

                        // ── 5. Music ──────────────────────────────────────────────────────
                        launch { audio.startMusic() }
                    }

                    onIntent<GameStore.Intent.Start> {
                        if (engine.state.value.currentPieces.isEmpty()) engine.startNewGame()
                    }
                    onIntent<GameStore.Intent.Place> { intent ->
                        engine.placePiece(intent.pieceId, intent.x, intent.y)
                    }
                    onIntent<GameStore.Intent.Revive> {
                        launch {
                            engine.continueWithSmallBlocks()
                            audio.startMusic() // restart music after revive
                        }
                    }
                    onIntent<GameStore.Intent.Restart> {
                        launch {
                            engine.startNewGame(bestScore = engine.state.value.bestScore)
                            audio.startMusic() // restart music on new game
                        }
                    }
                },
                reducer = GameReducer,
                bootstrapper = SimpleBootstrapper(GameStore.Action.Init),
            ) {}

    internal object GameReducer : Reducer<GameStoreState, GameStore.Msg> {
        override fun GameStoreState.reduce(msg: GameStore.Msg): GameStoreState = when (msg) {
            is GameStore.Msg.Snapshot -> copy(game = msg.state)
            is GameStore.Msg.CountdownTick -> copy(continueCountdown = msg.secondsRemaining)
        }
    }
}
