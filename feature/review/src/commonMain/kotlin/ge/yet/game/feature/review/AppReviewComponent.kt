package ge.yet.game.feature.review

import com.arkivanov.decompose.ComponentContext

interface AppReviewComponent {
    fun onDontShowAgainClicked()
    fun onLeaveFeedbackClicked()

    fun interface Factory {
        fun create(
            componentContext: ComponentContext,
            analyticsParams: Map<String, Any>,
            onCloseRequested: () -> Unit,
        ): AppReviewComponent
    }
}
