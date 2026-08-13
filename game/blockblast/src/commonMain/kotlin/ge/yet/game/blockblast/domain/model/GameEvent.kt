package ge.yet.game.blockblast.domain.model

import ge.yet.game.blockblast.domain.model.FeedbackType
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface GameEvent {
    @Serializable
    data class MoveResolved(
        val pieceId: Long,
        val placedCellCount: Int,
        val clearedCells: List<Position>,
        val linesCount: Int,
        val isCrossClear: Boolean,
        val isBoardEmpty: Boolean,
        val placementPoints: Long,
        val clearPoints: Long,
        val allClearPoints: Long,
        val totalPoints: Long,
        val comboLevel: Int,
        val movesWithoutClear: Int,
        val feedback: FeedbackType?,
        val isGameOver: Boolean,
    ) : GameEvent

    /** Round began (fresh game, restored save, or post-revive). Music starts here. */
    @Serializable
    data object GameStarted : GameEvent
}
