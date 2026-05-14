package ge.yet.blockblast.feature.game.reviewprompt

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultReviewPromptComponentTest {

    @Test
    fun onDontShowAgainClicked_dismisses_sheet_without_requesting_review() {
        var dismissCount = 0
        var dontShowAgainCount = 0
        var reviewCount = 0
        val component = DefaultReviewPromptComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            onDontShowAgainRequested = { dontShowAgainCount += 1 },
            onDismissed = { dismissCount += 1 },
            onReviewRequested = { reviewCount += 1 },
        )

        component.onDontShowAgainClicked()

        assertEquals(1, dontShowAgainCount)
        assertEquals(1, dismissCount)
        assertEquals(0, reviewCount)
    }

    @Test
    fun onLeaveFeedbackClicked_requests_review_and_dismisses_sheet() {
        var dismissCount = 0
        var reviewCount = 0
        val component = DefaultReviewPromptComponent(
            componentContext = DefaultComponentContext(LifecycleRegistry()),
            onDontShowAgainRequested = {},
            onDismissed = { dismissCount += 1 },
            onReviewRequested = { reviewCount += 1 },
        )

        component.onLeaveFeedbackClicked()

        assertEquals(1, reviewCount)
        assertEquals(1, dismissCount)
    }
}
