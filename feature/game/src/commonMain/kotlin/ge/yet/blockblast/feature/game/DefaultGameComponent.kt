package ge.yet.blockblast.feature.game

import com.app.common.decompose.asValue
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.integration.stateToModel
import ge.yet.blockblast.feature.game.store.GameAnalyticsLogger
import ge.yet.blockblast.feature.game.tray.DefaultPieceTrayComponent
import ge.yet.blockblast.feature.game.tray.PieceTrayComponent
import ge.yet.blockblast.feature.game.store.GameStore
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet.blokblast.domain.model.GameState
import ge.yet.blokblast.domain.repository.AnalyticRepository
import ge.yet.blokblast.domain.repository.AudioRepository
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

internal class DefaultGameComponent(
    componentContext: ComponentContext,
    analytics: AnalyticRepository,
    private val gameStoreFactory: GameStoreFactory,
    private val settingsComponent: SettingsComponent.Factory,
    private val audio: AudioRepository,
    private val isNewGame: Boolean,
    private val restoredResultState: GameState?,
    private val onExitClickedCb: () -> Unit,
    private val onGameCompletedCb: (GameState, Boolean, Boolean) -> Unit,
    private val onReviveCompletedCb: (GameState) -> Unit,
    private val onReviveFailedCb: () -> Unit,
) : ComponentContext by componentContext,
    GameComponent {
    private val store = instanceKeeper.getStore {
        gameStoreFactory.create(
            isNewGame = isNewGame,
            restoredResultState = restoredResultState,
        )
    }
    private val sheetNavigation = SlotNavigation<SheetConfig>()
    private val lifecycleScope = coroutineScope()
    private val logger = GameAnalyticsLogger(analytics)

    override val model: Value<GameComponent.Model> = store.asValue().map(stateToModel)

    override val pieceTray: PieceTrayComponent = DefaultPieceTrayComponent(
        componentContext = childContext(key = "PieceTray"),
        state = store.asValue(),
    )

    override val sheetSlot: Value<ChildSlot<*, GameComponent.SheetChild>> =
        childSlot(
            source = sheetNavigation,
            serializer = SheetConfig.serializer(),
            key = "GameSheet",
            handleBackButton = true,
            childFactory = ::createSheetChild,
        )

    init {
        // Stop music when the user navigates away (back button or exit)
        lifecycle.doOnDestroy { lifecycleScope.launch { audio.stopMusic() } }
        // One-shot effects from the store. Per the mvikotlin-code skill,
        // navigation/SDK calls live in the component, not the executor.
        lifecycleScope.launch {
            store.labels.collect { label ->
                when (label) {
                    is GameStore.Label.GameCompleted ->
                        onGameCompletedCb(
                            label.finalState,
                            label.canContinue,
                            label.shouldRequestReview,
                        )
                    is GameStore.Label.ReviveCompleted ->
                        onReviveCompletedCb(label.playableState)
                    GameStore.Label.ReviveFailed -> onReviveFailedCb()
                }
            }
        }
    }

    override fun onCellClicked(pieceId: Long, x: Int, y: Int) {
        store.accept(GameStore.Intent.Place(pieceId, x, y))
    }

    override fun onReviveClicked() = store.accept(GameStore.Intent.Revive)
    override fun onRestartClicked() = store.accept(GameStore.Intent.Restart)
    override fun onSettingsClicked() {
        log("settings_opened")
        sheetNavigation.activate(SheetConfig.Settings)
    }

    override fun onExitClicked() {
        log("exit_clicked")
        onExitClickedCb()
    }

    override fun onDismissSheet() {
        val dismissedChild = sheetSlot.value.child?.instance
        when (dismissedChild) {
            is GameComponent.SheetChild.Settings -> log("settings_closed")
            null -> Unit
        }
        sheetNavigation.dismiss()
    }

    private fun log(eventName: String) = logger.log(eventName, store.state)

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
    private val audio: AudioRepository,
    private val analytics: AnalyticRepository,
) : GameComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        isNewGame: Boolean,
        restoredResultState: GameState?,
        onExitClicked: () -> Unit,
        onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
        onReviveCompleted: (GameState) -> Unit,
        onReviveFailed: () -> Unit,
    ): GameComponent = DefaultGameComponent(
        componentContext = componentContext,
        gameStoreFactory = gameStoreFactory,
        settingsComponent = settingsComponent,
        audio = audio,
        analytics = analytics,
        isNewGame = isNewGame,
        restoredResultState = restoredResultState,
        onExitClickedCb = onExitClicked,
        onGameCompletedCb = onGameCompleted,
        onReviveCompletedCb = onReviveCompleted,
        onReviveFailedCb = onReviveFailed,
    )
}
