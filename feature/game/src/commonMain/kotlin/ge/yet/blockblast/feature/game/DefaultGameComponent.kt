package ge.yet.blockblast.feature.game

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.store.GameStore
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blokblast.domain.model.GameState

internal class DefaultGameComponent(
    componentContext: ComponentContext,
    private val gameStoreFactory: GameStoreFactory,
    private val onSettingsClickedCb: () -> Unit,
    private val onExitClickedCb: () -> Unit,
) : ComponentContext by componentContext,
    GameComponent {
    private val store = instanceKeeper.getStore { gameStoreFactory.create() }

    override val model: Value<GameState> = store.asValue()

    init {
        lifecycle.doOnStart { store.accept(GameStore.Intent.Start) }
    }


    override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
        store.accept(GameStore.Intent.Place(pieceId, x, y))
    }

    override fun onReviveClicked() = store.accept(GameStore.Intent.Revive)
    override fun onRestartClicked() = store.accept(GameStore.Intent.Restart)
    override fun onSettingsClicked() = onSettingsClickedCb()
    override fun onExitClicked() = onExitClickedCb()

}


@Inject
internal class DefaultGameComponentFactory(
    private val gameStoreFactory: GameStoreFactory,
) : GameComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onSettingsClicked: () -> Unit,
        onExitClicked: () -> Unit,
    ): GameComponent = DefaultGameComponent(
        componentContext = componentContext,
        gameStoreFactory = gameStoreFactory,
        onSettingsClickedCb = onSettingsClicked,
        onExitClickedCb = onExitClicked,
    )
}
