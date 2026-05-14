package ge.yet.blockblast.feature.game.reviewprompt

import com.arkivanov.decompose.ComponentContext

interface ReviewPromptComponent {
    fun onDontShowAgainClicked()
    fun onLeaveFeedbackClicked()
}

internal class DefaultReviewPromptComponent(
    componentContext: ComponentContext,
    private val onDontShowAgainRequested: () -> Unit,
    private val onDismissed: () -> Unit,
    private val onReviewRequested: () -> Unit,
) : ReviewPromptComponent,
    ComponentContext by componentContext {

    override fun onDontShowAgainClicked() {
        onDontShowAgainRequested()
        onDismissed()
    }

    override fun onLeaveFeedbackClicked() {
        onReviewRequested()
        onDismissed()
    }
}
