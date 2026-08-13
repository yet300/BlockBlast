package ge.yet.game.blockblast.component.review

import com.arkivanov.decompose.ComponentContext

interface AppReviewComponent {
    fun onDontShowAgainClicked()
    fun onLeaveFeedbackClicked()
}

internal class DefaultAppReviewComponent(
    componentContext: ComponentContext,
    private val onDontShowAgainRequested: () -> Unit,
    private val onDismissed: () -> Unit,
    private val onReviewRequested: () -> Unit,
) : AppReviewComponent,
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
