package ge.yet.game.twentyfortyeight.component

import com.app.common.decompose.asValue
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.slot.ChildSlot
import com.arkivanov.decompose.router.slot.SlotNavigation
import com.arkivanov.decompose.router.slot.activate
import com.arkivanov.decompose.router.slot.childSlot
import com.arkivanov.decompose.router.slot.dismiss
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import com.arkivanov.decompose.value.operator.map
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.engine.GamePhase
import ge.yet.game.twentyfortyeight.engine.GameStatistics
import ge.yet.game.twentyfortyeight.engine.RuntimeBoard
import ge.yet.game.twentyfortyeight.store.BootstrapState
import ge.yet.game.twentyfortyeight.store.OverlayState
import ge.yet.game.twentyfortyeight.store.TwentyFortyEightStore
import ge.yet.game.twentyfortyeight.store.VisualTransition
import kotlinx.serialization.Serializable

internal interface PlayingComponent {
    val model: Value<Model>
    val overlay: Value<ChildSlot<*, OverlayComponent>>

    fun onMove(direction: Direction)
    fun onUndoRequested()
    fun onRestartRequested()
    fun onContinueAfterVictory()
    fun onTutorialSkipped()
    fun onAnimationCompleted(transitionId: Long)
    fun onStatisticsRequested()
    fun handleBack(): Boolean

    data class Model(
        val board: RuntimeBoard?,
        val transition: VisualTransition?,
        val score: Long,
        val bestScore: Long,
        val gesturesEnabled: Boolean,
        val undoEnabled: Boolean,
        val tutorialVisible: Boolean,
        val overlay: OverlayState?,
        val persistenceStatus: PersistenceStatus,
    )

    enum class PersistenceStatus { Clean, Saving, Dirty }
}

internal class DefaultPlayingComponent(
    componentContext: ComponentContext,
    private val store: TwentyFortyEightStore,
) : PlayingComponent,
    ComponentContext by componentContext {

    private val overlayNavigation = SlotNavigation<OverlayConfig>()
    private var componentAlive = true

    override val model: Value<PlayingComponent.Model> = store.asValue().map { state ->
        val game = state.game
        PlayingComponent.Model(
            board = game?.board,
            transition = state.activeTransition,
            score = game?.score ?: 0L,
            bestScore = game?.bestScore ?: 0L,
            gesturesEnabled = state.bootstrap == BootstrapState.Ready &&
                state.visibility == MiniAppVisibility.ACTIVE &&
                game?.phase == GamePhase.Playing &&
                state.overlay == null,
            undoEnabled = state.bootstrap == BootstrapState.Ready &&
                state.visibility == MiniAppVisibility.ACTIVE &&
                game?.phase == GamePhase.Playing &&
                game.undo != null &&
                state.activeTransition == null &&
                state.overlay == null,
            tutorialVisible = state.bootstrap == BootstrapState.Ready && !state.tutorialSeen,
            overlay = state.overlay,
            persistenceStatus = when {
                state.persistenceDirty -> PlayingComponent.PersistenceStatus.Dirty
                state.requestedRevision > state.durableRevision -> PlayingComponent.PersistenceStatus.Saving
                else -> PlayingComponent.PersistenceStatus.Clean
            },
        )
    }

    override val overlay: Value<ChildSlot<*, OverlayComponent>> = childSlot(
        source = overlayNavigation,
        serializer = OverlayConfig.serializer(),
        key = "TwentyFortyEightOverlay",
        handleBackButton = false,
        childFactory = ::createOverlay,
    )

    init {
        val cancellation = store.asValue().subscribe { state ->
            val desired = state.overlay?.toConfig()
            val current = overlay.value.child?.configuration
            when {
                desired == null && current != null -> overlayNavigation.dismiss()
                desired != null && desired != current -> overlayNavigation.activate(desired)
            }
        }
        lifecycle.doOnDestroy {
            componentAlive = false
            cancellation.cancel()
        }
    }

    override fun onMove(direction: Direction) = store.accept(TwentyFortyEightStore.Intent.Move(direction))
    override fun onUndoRequested() = store.accept(TwentyFortyEightStore.Intent.Undo)
    override fun onRestartRequested() = store.accept(TwentyFortyEightStore.Intent.RequestRestart)
    override fun onContinueAfterVictory() = store.accept(TwentyFortyEightStore.Intent.ContinueAfterVictory)
    override fun onTutorialSkipped() = store.accept(TwentyFortyEightStore.Intent.SkipTutorial)
    override fun onAnimationCompleted(transitionId: Long) =
        store.accept(TwentyFortyEightStore.Intent.AnimationCompleted(transitionId))
    override fun onStatisticsRequested() = store.accept(TwentyFortyEightStore.Intent.OpenStatistics)

    override fun handleBack(): Boolean {
        if (overlay.value.child == null) return false
        store.accept(TwentyFortyEightStore.Intent.CancelOverlay)
        return true
    }

    private fun createOverlay(
        config: OverlayConfig,
        @Suppress("UNUSED_PARAMETER") componentContext: ComponentContext,
    ): OverlayComponent {
        val state = store.state
        val game = state.game
        return when (config) {
            OverlayConfig.Victory -> {
                lateinit var origin: OverlayComponent.Victory
                origin = OverlayComponent.Victory(
                    model = MutableValue(
                        OverlayComponent.Model.Victory(game?.score ?: 0L, game?.bestScore ?: 0L),
                    ),
                    onContinue = {
                        acceptFromOverlay(origin, TwentyFortyEightStore.Intent.ContinueAfterVictory)
                    },
                    onRestart = { acceptFromOverlay(origin, TwentyFortyEightStore.Intent.RequestRestart) },
                    onDismiss = { acceptFromOverlay(origin, TwentyFortyEightStore.Intent.CancelOverlay) },
                )
                origin
            }
            OverlayConfig.Statistics -> {
                lateinit var origin: OverlayComponent.Statistics
                origin = OverlayComponent.Statistics(
                    model = MutableValue(state.statistics.toOverlayModel()),
                    onDismiss = { acceptFromOverlay(origin, TwentyFortyEightStore.Intent.CancelOverlay) },
                )
                origin
            }
            OverlayConfig.RestartConfirmation -> {
                lateinit var origin: OverlayComponent.RestartConfirmation
                origin = OverlayComponent.RestartConfirmation(
                    model = MutableValue(
                        OverlayComponent.Model.RestartConfirmation(
                            score = game?.score ?: 0L,
                            successfulMovesInRun = game?.successfulMovesInRun ?: 0L,
                        ),
                    ),
                    onConfirm = { acceptFromOverlay(origin, TwentyFortyEightStore.Intent.ConfirmRestart) },
                    onDismiss = { acceptFromOverlay(origin, TwentyFortyEightStore.Intent.CancelOverlay) },
                )
                origin
            }
        }
    }

    private fun acceptFromOverlay(origin: OverlayComponent, intent: TwentyFortyEightStore.Intent) {
        if (componentAlive && overlay.value.child?.instance === origin) store.accept(intent)
    }
}

@Serializable
private enum class OverlayConfig { Victory, Statistics, RestartConfirmation }

private fun OverlayState.toConfig(): OverlayConfig = when (this) {
    OverlayState.Victory -> OverlayConfig.Victory
    OverlayState.Statistics -> OverlayConfig.Statistics
    OverlayState.RestartConfirmation -> OverlayConfig.RestartConfirmation
}

private fun GameStatistics.toOverlayModel() = OverlayComponent.Model.Statistics(
    gamesStarted = gamesStarted,
    gamesWon = gamesWon,
    gamesEndedByGameOver = gamesEndedByGameOver,
    successfulMoves = successfulMoves,
    totalMerges = totalMerges,
    totalScoreEarned = totalScoreEarned,
    highestTileEver = highestTileEver,
    undoUses = undoUses,
)
