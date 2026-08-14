package ge.yet.game.miniapp.testkit

import ge.yet.game.miniapp.api.MiniAppReviewOpportunity
import ge.yet.game.miniapp.api.MiniAppSessionHost

class RecordingMiniAppSessionHost : MiniAppSessionHost {
    var closeCount: Int = 0
        private set

    private val mutableReviews = mutableListOf<MiniAppReviewOpportunity>()
    val reviewRequests: List<MiniAppReviewOpportunity>
        get() = mutableReviews.toList()

    override fun close() {
        closeCount += 1
    }

    override fun requestReview(opportunity: MiniAppReviewOpportunity) {
        mutableReviews += opportunity
    }
}
