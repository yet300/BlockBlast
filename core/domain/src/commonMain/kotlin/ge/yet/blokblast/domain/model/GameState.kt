package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ClearEvent(val cells: List<Position> = emptyList(), val nonce: Int = 0)

@Serializable
data class FeedbackEvent(val type: FeedbackType? = null, val nonce: Int = 0)

@Serializable
data class PointsEvent(val points: Long = 0, val nonce: Int = 0)

@Serializable
data class GameState(
    val grid: Grid = Grid(),
    val score: Long = 0L,
    val bestScore: Long = 0L,
    val comboLevel: Int = 0,
    val currentPieces: List<Piece> = emptyList(),
    val isGameOver: Boolean = false,
    val revivesUsed: Int = 0,
    val lastClearedCells: ClearEvent = ClearEvent(),
    val lastFeedback: FeedbackEvent = FeedbackEvent(),
    val lastPointsAwarded: PointsEvent = PointsEvent(),
    /**
     * Snapshot of [bestScore] at the start of the current round. Used at
     * game-over to detect a *new* personal best that beat the previous best
     * by [REVIEW_BEST_SCORE_DELTA] points (review-prompt qualifier). Lives
     * on [GameState] rather than as a store-executor local so it survives
     * store recreation when the user navigates Home → Play.
     */
    val bestAtRoundStart: Long = 0L,
    /**
     * Whether an in-app-review prompt has already fired for the current
     * game-over event. Prevents the prompt from re-firing if the user exits
     * the game-over screen, returns, and re-enters the same round.
     */
    val reviewPromptFiredThisRound: Boolean = false,
) {
    companion object {
        const val MAX_REVIVES = 1
    }
}
