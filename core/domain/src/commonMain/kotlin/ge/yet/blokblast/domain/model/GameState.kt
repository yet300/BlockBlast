package ge.yet.blokblast.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GameState(
    val grid: Grid = Grid(),
    val score: Long = 0L,
    val bestScore: Long = 0L,
    val comboLevel: Int = 0,
    val currentPieces: List<Piece> = emptyList(),
    val isGameOver: Boolean = false,
    val revivesUsed: Int = 0,
) {
    companion object {
        const val MAX_REVIVES = 1
    }
}
