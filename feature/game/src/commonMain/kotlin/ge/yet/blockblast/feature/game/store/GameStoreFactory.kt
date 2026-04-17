package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.model.GameEvent
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.repository.AudioRepository
import kotlinx.coroutines.launch

@Inject
internal class GameStoreFactory(
    private val storeFactory: StoreFactory,
    private val engine: GameEngine,
    private val audio: AudioRepository,
) {
    fun create(): GameStore =
        object :
            GameStore,
            Store<GameStore.Intent, GameState, Nothing> by storeFactory.create(
                name = "GameStore",
                initialState = engine.state.value,
                executorFactory = coroutineExecutorFactory<GameStore.Intent, GameStore.Action, GameState, GameStore.Msg, Nothing> {
                    onAction<GameStore.Action> {
                        launch {
                            engine.state.collect { dispatch(GameStore.Msg.Snapshot(it)) }
                        }
                        // Collect one-shot events → trigger audio
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

    internal object GameReducer : Reducer<GameState, GameStore.Msg> {
        override fun GameState.reduce(msg: GameStore.Msg): GameState = when (msg) {
            is GameStore.Msg.Snapshot -> msg.state
        }
    }
}
