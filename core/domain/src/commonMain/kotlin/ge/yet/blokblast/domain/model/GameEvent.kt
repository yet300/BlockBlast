package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameEvent {
    @Serializable
    data class PiecePlaced(val points: Long) : GameEvent

    @Serializable
    data class LinesCleared(
        val clearedCells: List<Position>,
        val linesCount: Int,
        val isCrossClear: Boolean,
    ) : GameEvent

    @Serializable
    data class ComboActive(val level: Int) : GameEvent

    @Serializable
    data class Feedback(val type: FeedbackType) : GameEvent

    @Serializable
    data object GameOver : GameEvent
}
