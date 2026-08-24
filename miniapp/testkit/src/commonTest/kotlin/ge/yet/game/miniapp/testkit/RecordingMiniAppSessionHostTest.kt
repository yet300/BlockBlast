package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingMiniAppSessionHostTest {
    @Test
    fun `repeated close and review calls are recorded`() {
        val host = RecordingMiniAppSessionHost()
        val opportunity = MiniAppReviewOpportunity(triggerId = "counter.increment", score = 2)

        host.close()
        host.close()
        host.requestReview(opportunity)
        host.requestReview(opportunity)

        assertEquals(2, host.closeCount)
        assertEquals(listOf(opportunity, opportunity), host.reviewRequests)
    }
}
