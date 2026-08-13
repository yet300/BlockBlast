package ge.yet.game.feature.review.policy

interface AppReviewPolicy {
    suspend fun tryAcquirePrompt(): Boolean
}
