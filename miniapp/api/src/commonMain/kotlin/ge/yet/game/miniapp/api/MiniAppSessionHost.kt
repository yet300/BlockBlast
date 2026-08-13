package ge.yet.game.miniapp.api

interface MiniAppSessionHost {

    fun close()

    fun requestReview(opportunity: MiniAppReviewOpportunity)
}
