package ge.yet.blockblast.feature.game.store

import com.arkivanov.mvikotlin.core.store.Reducer
import com.arkivanov.mvikotlin.core.store.SimpleBootstrapper
import com.arkivanov.mvikotlin.core.store.Store
import com.arkivanov.mvikotlin.core.store.StoreFactory
import com.arkivanov.mvikotlin.extensions.coroutines.coroutineExecutorFactory
import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.engine.GameEngine
import ge.yet.blokblast.domain.model.GameState
import kotlinx.coroutines.launch

@Inject
internal class GameStoreFactory(
    private val storeFactory: StoreFactory,
    private val engine: GameEngine,
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
                    }
                    onIntent<GameStore.Intent.Start> {
                        if (engine.state.value.currentPieces.isEmpty()) engine.startNewGame()
                    }
                    onIntent<GameStore.Intent.Place> { intent ->
                        engine.placePiece(intent.pieceId, intent.x, intent.y)
                    }
                    onIntent<GameStore.Intent.Revive> {
                        engine.continueWithSmallBlocks()
                    }
                    onIntent<GameStore.Intent.Restart> {
                        engine.startNewGame(bestScore = engine.state.value.bestScore)
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
