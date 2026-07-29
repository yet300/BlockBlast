package ge.yet.blockblast.feature.game.result

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value

interface GameResultComponent {

    val model: Value<Model>

    fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit)
    fun onHomeClicked()

    data class Model(
        val snapshot: BlockBlastResultSnapshot,
        val canContinue: Boolean,
        val continueSecondsRemaining: Int,
    ) {
        val isContinuePhase: Boolean
            get() = canContinue && continueSecondsRemaining > 0
    }

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            snapshot: BlockBlastResultSnapshot,
            canContinue: Boolean,
            onContinueRequested: () -> Unit,
            onNewGameRequested: () -> Unit,
            onHomeRequested: () -> Unit,
        ): GameResultComponent
    }
}
