package ge.yet.game.blockblast.component.result

import ge.yet.game.blockblast.domain.model.GameState
import ge.yet.game.blockblast.domain.model.Grid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockBlastResultSnapshotTest {

    @Test
    fun from_copies_the_final_grid() {
        val cells = IntArray(Grid.SIZE * Grid.SIZE) { Grid.EMPTY }
        cells[0] = 3
        val state = GameState(grid = Grid(cells), score = 120L, bestScore = 200L)

        val snapshot = BlockBlastResultSnapshot.from(state)
        cells[0] = 5

        assertEquals(3, snapshot.finalGrid.cells[0])
    }

    @Test
    fun from_marks_a_score_above_the_round_start_best_as_new() {
        val snapshot = BlockBlastResultSnapshot.from(
            GameState(
                score = 201L,
                bestScore = 201L,
                bestAtRoundStart = 200L,
            ),
        )

        assertTrue(snapshot.isNewBest)
    }

    @Test
    fun from_does_not_mark_a_tied_round_start_best_as_new() {
        val snapshot = BlockBlastResultSnapshot.from(
            GameState(
                score = 200L,
                bestScore = 200L,
                bestAtRoundStart = 200L,
            ),
        )

        assertFalse(snapshot.isNewBest)
    }
}
