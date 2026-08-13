package ge.yet.game.feature.review.policy

interface AppReviewPolicy {
    suspend fun tryAcquirePrompt(): Boolean

    /** Returns a reservation that could not be presented because app navigation changed. */
    suspend fun releasePrompt()
}
