package ge.yet.game.blockblast.component.result

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
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
