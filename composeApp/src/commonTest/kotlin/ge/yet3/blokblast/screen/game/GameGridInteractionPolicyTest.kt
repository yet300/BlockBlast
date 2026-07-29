package ge.yet3.blokblast.screen.game

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GameGridInteractionPolicyTest {

    @Test
    fun explicitly_non_interactive_grid_omits_cell_taps_even_with_a_piece() {
        assertFalse(gridCellTapEnabled(interactive = false, hasSelectedPiece = true))
    }

    @Test
    fun grid_without_a_selected_piece_omits_cell_taps() {
        assertFalse(gridCellTapEnabled(interactive = true, hasSelectedPiece = false))
    }

    @Test
    fun interactive_grid_with_a_selected_piece_enables_cell_taps() {
        assertTrue(gridCellTapEnabled(interactive = true, hasSelectedPiece = true))
    }
}
