package ge.yet.blockblast.feature.game

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.doOnStart
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.store.GameStore
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blokblast.domain.model.GameState
import kotlinx.serialization.Serializable

internal class DefaultGameComponent(
    componentContext: ComponentContext,
    private val gameStoreFactory: GameStoreFactory,
    private val settingsComponent: SettingsComponent.Factory,
    private val onExitClickedCb: () -> Unit,
) : ComponentContext by componentContext,
    GameComponent {
    private val store = instanceKeeper.getStore { gameStoreFactory.create() }
    private val sheetNavigation = SlotNavigation<SheetConfig>()

    override val model: Value<GameState> = store.asValue()

    override val sheetSlot: Value<ChildSlot<*, GameComponent.SheetChild>> =
        childSlot(
            source = sheetNavigation,
            serializer = SheetConfig.serializer(),
            key = "GameSheet",
            handleBackButton = true,
            childFactory = ::createSheetChild,
        )


    init {
        lifecycle.doOnStart { store.accept(GameStore.Intent.Start) }
    }


    override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
        store.accept(GameStore.Intent.Place(pieceId, x, y))
    }

    override fun onReviveClicked() = store.accept(GameStore.Intent.Revive)
    override fun onRestartClicked() = store.accept(GameStore.Intent.Restart)
    override fun onSettingsClicked() = sheetNavigation.activate(SheetConfig.Settings)
    override fun onExitClicked() = onExitClickedCb()
    override fun onDismissSheet() = sheetNavigation.dismiss()

    private fun createSheetChild(
        config: SheetConfig,
        componentContext: ComponentContext,
    ): GameComponent.SheetChild =
        when (config) {
            is SheetConfig.Settings ->
                GameComponent.SheetChild.Settings(
                    settingsComponent.create(
                        componentContext = componentContext,
                        onBackClicked = ::onDismissSheet
                    ),
                )
        }

    @Serializable
    sealed interface SheetConfig {
        @Serializable
        data object Settings : SheetConfig
    }
}


@Inject
internal class DefaultGameComponentFactory(
    private val gameStoreFactory: GameStoreFactory,
    private val settingsComponent: SettingsComponent.Factory,
) : GameComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        onExitClicked: () -> Unit,
    ): GameComponent = DefaultGameComponent(
        componentContext = componentContext,
        gameStoreFactory = gameStoreFactory,
        settingsComponent = settingsComponent,
        onExitClickedCb = onExitClicked,
    )
}
