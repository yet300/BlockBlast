package ge.yet.game.feature.settings.reset

import com.app.common.decompose.componentCoroutineScope
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.MutableValue
import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.api.MiniAppDataResetResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class DefaultResetGameDataComponent(
    componentContext: ComponentContext,
    private val clearGameData: suspend () -> MiniAppDataResetResult,
    private val onBackClickedCb: () -> Unit,
    private val coroutineScope: CoroutineScope = componentContext.componentCoroutineScope(),
) : ResetGameDataComponent, ComponentContext by componentContext {
    private val modelState = MutableValue(
        ResetGameDataComponent.Model(ResetGameDataComponent.Status.Confirming),
    )
    override val model: Value<ResetGameDataComponent.Model> = modelState

    override fun onConfirmClicked() = startClear(allowFromPartialFailure = false)

    override fun onRetryClicked() = startClear(allowFromPartialFailure = true)

    override fun onBackClicked() = onBackClickedCb()

    private fun startClear(allowFromPartialFailure: Boolean) {
        val status = modelState.value.status
        val canStart = status is ResetGameDataComponent.Status.Confirming ||
            (allowFromPartialFailure && status is ResetGameDataComponent.Status.PartialFailure)
        if (!canStart) return

        modelState.value = ResetGameDataComponent.Model(ResetGameDataComponent.Status.Clearing)
        coroutineScope.launch {
            val status = when (val result = clearGameData()) {
                MiniAppDataResetResult.Success -> ResetGameDataComponent.Status.Success
                is MiniAppDataResetResult.PartialFailure ->
                    ResetGameDataComponent.Status.PartialFailure(result.failedMiniAppIds)
            }
            modelState.value = ResetGameDataComponent.Model(status)
        }
    }
}
