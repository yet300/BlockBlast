package ge.yet.game.miniapp.api

data class MiniAppReviewOpportunity(
    val triggerId: String,
    val score: Long? = null,
    val bestScore: Long? = null,
    val revivesUsed: Int? = null,
)
