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
) {
    companion object {
        const val MAX_REVIVES = 1
    }
}
