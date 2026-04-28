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
                    game = engine.state.value,
                    continueCountdown = GameStoreState.COUNTDOWN_INACTIVE,
                ),
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameStoreState, GameStore.Msg, Nothing> {
                    onAction<GameStore.Action> {
                        // ── 0. Seed the engine with the persisted best score ──────────────
                        // GameEngine starts at bestScore = 0; without seeding it, the
                        // first `engine.state` emission (section 1) would overwrite our
                        // initialState with that 0, and `max(currentBest, score)` in
                        // placePiece would collapse "Best" onto the current round's
                        // score. seedBestScore is a no-op if the engine already knows a
                        // higher value (e.g. carried over from an earlier session in
                        // the same process), so it is safe to run on every bootstrap.
                        // Synchronous, so it lands in the StateFlow before section 1's
                        // collect subscribes.
                        engine.seedBestScore(settings.bestScore.value)

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
                        // We need *real* edges (false→true / true→false), not the
                        // initial replay value of the StateFlow. If the user
                        // exits the game while the game-over overlay is up and
                        // re-enters, a new GameStore subscribes to engine.state
                        // and the very first emission would be `true` again —
                        // distinctUntilChanged() would let it through and the
                        // collector would (incorrectly) start a fresh 5s
                        // countdown. So we seed `previous` from the current
                        // engine value and only react when it changes.
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
                            var previousIsGameOver = engine.state.value.isGameOver

                            engine.state
                                .map { it.isGameOver }
                                .collect { isGameOver ->
                                    if (isGameOver == previousIsGameOver) return@collect
                                    previousIsGameOver = isGameOver
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
                        // GameEngine is AppScope-scoped, so it survives the
                        // Game component being popped & re-pushed (Home → Play).
                        // Without this, returning to Home after game-over and
                        // pressing Play would re-show the dead board because
                        // currentPieces wasn't empty.
                        val s = engine.state.value
                        if (s.currentPieces.isEmpty() || s.isGameOver) {
                            engine.startNewGame(bestScore = s.bestScore)
                            launch { audio.startMusic() }
                        }
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
