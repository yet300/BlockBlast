package ge.yet.blockblast.feature.game.result

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class DefaultGameResultComponent(
    componentContext: ComponentContext,
    snapshot: BlockBlastResultSnapshot,
    canContinue: Boolean,
    private val onContinueRequested: () -> Unit,
    private val onNewGameRequested: () -> Unit,
    private val onHomeRequested: () -> Unit,
) : GameResultComponent,
    ComponentContext by componentContext {

    private val componentScope = coroutineScope()
    private val modelState = MutableValue(
        GameResultComponent.Model(
            snapshot = snapshot,
            canContinue = canContinue,
            continueSecondsRemaining = if (canContinue) CONTINUE_COUNTDOWN_SECONDS else 0,
        ),
    )
    override val model: Value<GameResultComponent.Model> = modelState

    private var countdownJob: Job? = null
    private var terminalActionHandled = false

    init {
        if (modelState.value.isContinuePhase) {
            countdownJob = componentScope.launch {
                while (modelState.value.continueSecondsRemaining > 0) {
                    delay(COUNTDOWN_TICK_MILLIS)
                    modelState.value = modelState.value.copy(
                        continueSecondsRemaining =
                            (modelState.value.continueSecondsRemaining - 1).coerceAtLeast(0),
                    )
                }
            }
        }
    }

    override fun onPrimaryClicked() {
        if (!claimTerminalAction()) return
        countdownJob?.cancel()

        if (modelState.value.isContinuePhase) {
            onContinueRequested()
        } else {
            onNewGameRequested()
        }
    }

    override fun onHomeClicked() {
        if (!claimTerminalAction()) return
        countdownJob?.cancel()
        onHomeRequested()
    }

    private fun claimTerminalAction(): Boolean {
        if (terminalActionHandled) return false
        terminalActionHandled = true
        return true
    }

    private companion object {
        const val CONTINUE_COUNTDOWN_SECONDS = 5
        const val COUNTDOWN_TICK_MILLIS = 1_000L
    }
}

@Inject
internal class DefaultGameResultComponentFactory : GameResultComponent.Factory {
    override fun create(
        componentContext: ComponentContext,
        snapshot: BlockBlastResultSnapshot,
        canContinue: Boolean,
        onContinueRequested: () -> Unit,
        onNewGameRequested: () -> Unit,
        onHomeRequested: () -> Unit,
    ): GameResultComponent =
        DefaultGameResultComponent(
            componentContext = componentContext,
            snapshot = snapshot,
            canContinue = canContinue,
            onContinueRequested = onContinueRequested,
            onNewGameRequested = onNewGameRequested,
            onHomeRequested = onHomeRequested,
        )
}
