package ge.yet.game.feature.settings.reset

import com.arkivanov.decompose.value.Value
import ge.yet.game.miniapp.api.MiniAppId

interface ResetGameDataComponent {
    val model: Value<Model>

    fun onConfirmClicked()
    fun onRetryClicked()
    fun onBackClicked()

    data class Model(val status: Status)

    sealed interface Status {
        data object Confirming : Status
        data object Clearing : Status
        data object Success : Status
        data class PartialFailure(val failedMiniAppIds: Set<MiniAppId>) : Status
    }
}
