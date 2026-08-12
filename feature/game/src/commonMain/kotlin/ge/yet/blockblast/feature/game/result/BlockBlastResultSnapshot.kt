package ge.yet.blockblast.feature.game.result

import ge.yet.game.domain.model.GameState
import ge.yet.game.domain.model.Grid
import kotlinx.serialization.Serializable

@Serializable
data class BlockBlastResultSnapshot(
    val score: Long,
    val bestScore: Long,
    val finalGrid: Grid,
    val isNewBest: Boolean,
    val revivesUsed: Int,
) {
    companion object {
        fun from(state: GameState): BlockBlastResultSnapshot =
            BlockBlastResultSnapshot(
                score = state.score,
                bestScore = state.bestScore,
                finalGrid = Grid(state.grid.cells.copyOf()),
                isNewBest = state.score > state.bestAtRoundStart,
                revivesUsed = state.revivesUsed,
            )
    }
}
