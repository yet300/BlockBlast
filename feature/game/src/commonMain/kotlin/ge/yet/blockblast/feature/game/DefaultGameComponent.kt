package ge.yet.blockblast.feature.game

import com.app.common.decompose.asValue
import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.childContext
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.doOnDestroy
import com.arkivanov.mvikotlin.core.instancekeeper.getStore
import com.arkivanov.mvikotlin.extensions.coroutines.labels
import dev.zacsweers.metro.Inject
import ge.yet.blockblast.feature.game.integration.stateToModel
import ge.yet.blockblast.feature.game.store.GameAnalyticsLogger
import ge.yet.blockblast.feature.game.store.GameStore
import ge.yet.blockblast.feature.game.store.GameStoreFactory
import ge.yet.blockblast.feature.game.tray.DefaultPieceTrayComponent
import ge.yet.blockblast.feature.game.tray.PieceTrayComponent
import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.repository.AnalyticRepository
import ge.yet.game.domain.repository.AudioRepository
import kotlinx.coroutines.launch

internal class DefaultGameComponent(
    componentContext: ComponentContext,
    analytics: AnalyticRepository,
    private val gameStoreFactory: GameStoreFactory,
    private val audio: AudioRepository,
    private val isNewGame: Boolean,
    private val restoredResultState: GameState?,
    private val onSettingsClick: () -> Unit,
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
    private val lifecycleScope = coroutineScope()
    private val logger = GameAnalyticsLogger(analytics)

    override val model: Value<GameComponent.Model> = store.asValue().map(stateToModel)

    override val pieceTray: PieceTrayComponent = DefaultPieceTrayComponent(
        componentContext = childContext(key = "PieceTray"),
        state = store.asValue(),
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
        onSettingsClick()
    }

    override fun onExitClicked() {
        log("exit_clicked")
        onExitClickedCb()
    }

    private fun log(eventName: String) = logger.log(eventName, store.state)

}

@Inject
internal class DefaultGameComponentFactory(
    private val gameStoreFactory: GameStoreFactory,
    private val audio: AudioRepository,
    private val analytics: AnalyticRepository,
) : GameComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        isNewGame: Boolean,
        restoredResultState: GameState?,
        onSettingsClicked: () -> Unit,
        onExitClicked: () -> Unit,
        onGameCompleted: (GameState, Boolean, Boolean) -> Unit,
        onReviveCompleted: (GameState) -> Unit,
        onReviveFailed: () -> Unit,
    ): GameComponent = DefaultGameComponent(
        componentContext = componentContext,
        gameStoreFactory = gameStoreFactory,
        audio = audio,
        analytics = analytics,
        isNewGame = isNewGame,
        restoredResultState = restoredResultState,
        onSettingsClick = onSettingsClicked,
        onExitClickedCb = onExitClicked,
        onGameCompletedCb = onGameCompleted,
        onReviveCompletedCb = onReviveCompleted,
        onReviveFailedCb = onReviveFailed,
    )
}
