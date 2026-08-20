package ge.yet.game.blockblast.component.result

import com.app.common.decompose.coroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import dev.zacsweers.metro.Inject
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

internal class DefaultGameResultComponent(
    componentContext: ComponentContext,
    snapshot: BlockBlastResultSnapshot,
    canContinue: Boolean,
    private val visibility: MiniAppVisibilitySource,
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
        startCountdown()
    }

    override fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit) {
        if (!claimTerminalAction()) return
        val continueSelected = modelState.value.isContinuePhase
        countdownJob?.cancel()

        if (continueSelected) {
            var approvalHandled = false
            requestContinue {
                componentScope.launch {
                    if (approvalHandled) return@launch
                    approvalHandled = true
                    onContinueRequested()
                }
            }
        } else {
            onNewGameRequested()
        }
    }

    override fun onHomeClicked() {
        if (!claimTerminalAction()) return
        countdownJob?.cancel()
        onHomeRequested()
    }

    override fun onContinueFailed() {
        terminalActionHandled = false
        modelState.value = modelState.value.copy(
            continueSecondsRemaining = CONTINUE_COUNTDOWN_SECONDS,
        )
        startCountdown()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startCountdown() {
        countdownJob?.cancel()
        if (!modelState.value.isContinuePhase) return
        countdownJob = componentScope.launch {
            visibility.visibility
                .flatMapLatest { state ->
                    if (state == MiniAppVisibility.ACTIVE) {
                        flow {
                            repeat(modelState.value.continueSecondsRemaining) {
                                delay(COUNTDOWN_TICK_MILLIS)
                                emit(Unit)
                            }
                        }
                    } else {
                        emptyFlow()
                    }
                }
                .collect {
                    val current = modelState.value
                    if (current.continueSecondsRemaining <= 0) return@collect
                    modelState.value = current.copy(
                        continueSecondsRemaining = current.continueSecondsRemaining - 1,
                    )
                }
        }
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
internal class DefaultGameResultComponentFactory(
    private val visibility: MiniAppVisibilitySource,
) : GameResultComponent.Factory {
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
            visibility = visibility,
            onContinueRequested = onContinueRequested,
            onNewGameRequested = onNewGameRequested,
            onHomeRequested = onHomeRequested,
        )
}
