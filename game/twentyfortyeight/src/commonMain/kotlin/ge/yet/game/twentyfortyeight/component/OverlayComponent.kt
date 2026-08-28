package ge.yet.game.twentyfortyeight.component

import com.arkivanov.decompose.value.Value

internal sealed interface OverlayComponent {
    val model: Value<Model>

    fun onDismissRequested()

    sealed interface Model {
        data class Victory(
            val score: Long,
            val bestScore: Long,
        ) : Model

        data class RestartConfirmation(
            val score: Long,
            val successfulMovesInRun: Long,
        ) : Model
    }

    class Victory(
        override val model: Value<Model.Victory>,
        private val onContinue: () -> Unit,
        private val onRestart: () -> Unit,
        private val onDismiss: () -> Unit,
    ) : OverlayComponent {
        fun onContinueRequested() = onContinue()
        fun onRestartRequested() = onRestart()
        override fun onDismissRequested() = onDismiss()
    }

    class RestartConfirmation(
        override val model: Value<Model.RestartConfirmation>,
        private val onConfirm: () -> Unit,
        private val onDismiss: () -> Unit,
    ) : OverlayComponent {
        fun onConfirmRequested() = onConfirm()
        override fun onDismissRequested() = onDismiss()
    }
}
