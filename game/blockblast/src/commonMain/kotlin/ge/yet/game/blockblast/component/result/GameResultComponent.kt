package ge.yet.game.blockblast.component.result

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.value.Value
import ge.yet.game.blockblast.component.review.AppReviewComponent

interface GameResultComponent {

    val model: Value<Model>
    val reviewPrompt: Value<ReviewPromptSlot>

    fun onPrimaryClicked(requestContinue: (onApproved: () -> Unit) -> Unit)
    fun onHomeClicked()
    fun onContinueFailed()
    fun onDismissReviewPrompt(): Boolean

    data class Model(
        val snapshot: BlockBlastResultSnapshot,
        val canContinue: Boolean,
        val continueSecondsRemaining: Int,
    ) {
        val isContinuePhase: Boolean
            get() = canContinue && continueSecondsRemaining > 0
    }

    data class ReviewPromptSlot(
        val component: AppReviewComponent?,
    )

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            snapshot: BlockBlastResultSnapshot,
            canContinue: Boolean,
            shouldRequestReview: Boolean,
            onContinueRequested: () -> Unit,
            onNewGameRequested: () -> Unit,
            onHomeRequested: () -> Unit,
        ): GameResultComponent
    }
}
