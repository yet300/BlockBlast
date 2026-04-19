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
                        // Mirror engine state into the store + drive the
                        // Continue-button countdown off game-over transitions.
                        launch {
                            var countdownJob: Job? = null
                            var wasGameOver = engine.state.value.isGameOver
                            // Track the best score heading into this game-over
                            // transition so a "new personal best" is detectable
                            // even after the engine has already updated bestScore.
                            // Use the persisted best as the baseline so a
                            // brand-new process still measures "new personal
                            // best" against the player's lifetime peak.
                            var bestBeforeGameOver = maxOf(
                                engine.state.value.bestScore,
                                settings.bestScore.value,
                            )
                            var reviewRequested = false

                            engine.state.collect { gameState ->
                                dispatch(GameStore.Msg.Snapshot(gameState))

                                // Persist any new personal best as soon as the
                                // engine bumps it (mid-game, not just on
                                // game-over) so a crash never loses progress.
                                if (gameState.bestScore > settings.bestScore.value) {
                                    launch { settings.setBestScore(gameState.bestScore) }
                                }

                                val isNowGameOver = gameState.isGameOver
                                when {
                                    // false → true: start fresh countdown
                                    !wasGameOver && isNowGameOver -> {
                                        // In-app review is intentionally rare:
                                        //   1. score must clear REVIEW_MIN_SCORE
                                        //   2. score must beat the previous
                                        //      best by at least REVIEW_BEST_SCORE_DELTA
                                        //   3. lifetime prompt count must still
                                        //      be below REVIEW_MAX_PROMPTS
                                        // The OS SDK throttles further on top
                                        // of this, but the lifetime cap is the
                                        // hard ceiling — the dialog will never
                                        // appear more than REVIEW_MAX_PROMPTS
                                        // times for one user.
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
                                    }
                                    // true → false: cancel & clear
                                    wasGameOver && !isNowGameOver -> {
                                        countdownJob?.cancel()
                                        countdownJob = null
                                        // New round starts — arm the review
                                        // trigger again and snapshot the best
                                        // score to beat.
                                        reviewRequested = false
                                        bestBeforeGameOver = gameState.bestScore
                                        dispatch(
                                            GameStore.Msg.CountdownTick(
                                                GameStoreState.COUNTDOWN_INACTIVE,
                                            ),
                                        )
                                    }
                                }
                                if (!isNowGameOver) {
                                    // Keep tracking the current best so the
                                    // next game-over has an accurate baseline
                                    // (engine bumps bestScore mid-game).
                                    bestBeforeGameOver = maxOf(
                                        bestBeforeGameOver,
                                        gameState.bestScore,
                                    )
                                }
                                wasGameOver = isNowGameOver
                            }
                        }

                        // Collect one-shot domain events → trigger audio
                        launch {
                            engine.events.collect { event ->
                                when (event) {
                                    is GameEvent.PiecePlaced ->
                                        audio.playPlacementSound()

                                    is GameEvent.LinesCleared ->
                                        audio.playClearSound(event.linesCount)

                                    is GameEvent.Feedback ->
                                        audio.playVoiceFeedback(event.type)

                                    is GameEvent.ComboActive ->
                                        audio.playVoiceCombo(event.level)

                                    is GameEvent.GameOver ->
                                        audio.stopMusic()
                                }
                            }
                        }
                        // Start looping background music
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
