package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position

/** Hand-curated set of fixed-orientation polyominoes used by Block Blast. */
internal object ShapeCatalog {

    private fun shape(id: String, vararg xy: Pair<Int, Int>) =
        Polyomino(id, xy.map { (x, y) -> Position(x, y) })

    val SMALL: List<Polyomino> = listOf(
        shape("h2", 0 to 0, 1 to 0),
        shape("v2", 0 to 0, 0 to 1),
        shape("diag2_tlbr", 0 to 0, 1 to 1),
        shape("diag2_trbl", 1 to 0, 0 to 1),
    )

    val MEDIUM: List<Polyomino> = listOf(
        shape("h3", 0 to 0, 1 to 0, 2 to 0),
        shape("v3", 0 to 0, 0 to 1, 0 to 2),
        shape("sq2", 0 to 0, 1 to 0, 0 to 1, 1 to 1),
        shape("L_tl", 0 to 0, 0 to 1, 1 to 1),
        shape("L_tr", 0 to 0, 1 to 0, 1 to 1),
        shape("L_bl", 0 to 0, 0 to 1, 1 to 0),
        shape("L_br", 1 to 0, 0 to 1, 1 to 1),
        // 3-cell diagonals (both orientations)
        shape("diag3_tlbr", 0 to 0, 1 to 1, 2 to 2),
        shape("diag3_trbl", 2 to 0, 1 to 1, 0 to 2),
    )

    val LARGE: List<Polyomino> = listOf(
        shape("h4", 0 to 0, 1 to 0, 2 to 0, 3 to 0),
        shape("v4", 0 to 0, 0 to 1, 0 to 2, 0 to 3),
        shape("h5", 0 to 0, 1 to 0, 2 to 0, 3 to 0, 4 to 0),
        shape("v5", 0 to 0, 0 to 1, 0 to 2, 0 to 3, 0 to 4),
        shape("sq3", 0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2, 1 to 2, 2 to 2),
        shape("L3_tl", 0 to 0, 0 to 1, 0 to 2, 1 to 2, 2 to 2),
        shape("L3_tr", 2 to 0, 2 to 1, 0 to 2, 1 to 2, 2 to 2),
        shape("L3_bl", 0 to 0, 1 to 0, 2 to 0, 0 to 1, 0 to 2),
        shape("L3_br", 0 to 0, 1 to 0, 2 to 0, 2 to 1, 2 to 2),
        shape("T", 0 to 0, 1 to 0, 2 to 0, 1 to 1, 1 to 2),
        shape("T_down", 1 to 0, 1 to 1, 0 to 2, 1 to 2, 2 to 2),
        shape("T_left", 2 to 0, 0 to 1, 1 to 1, 2 to 1, 2 to 2),
        shape("T_right", 0 to 0, 0 to 1, 1 to 1, 2 to 1, 0 to 2),
        shape("plus", 1 to 0, 0 to 1, 1 to 1, 2 to 1, 1 to 2),
        shape("S", 1 to 0, 2 to 0, 0 to 1, 1 to 1),
        shape("Z", 0 to 0, 1 to 0, 1 to 1, 2 to 1),
        // S/Z rotated 90°
        shape("S_v", 0 to 0, 0 to 1, 1 to 1, 1 to 2),
        shape("Z_v", 1 to 0, 0 to 1, 1 to 1, 0 to 2),
        // 2x3 / 3x2 rectangles
        shape("rect_2x3", 0 to 0, 1 to 0, 0 to 1, 1 to 1, 0 to 2, 1 to 2),
        shape("rect_3x2", 0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1),
        // 4-cell tetromino L / J (2×3 footprint, each corner)
        shape("L4_tl", 0 to 0, 0 to 1, 0 to 2, 1 to 2),
        shape("L4_tr", 1 to 0, 1 to 1, 0 to 2, 1 to 2),
        shape("J4_tl", 0 to 0, 1 to 0, 1 to 1, 1 to 2),
        shape("J4_tr", 0 to 0, 1 to 0, 0 to 1, 0 to 2),
        // 5-cell "U" shape (like Block Blast's bucket)
        shape("U", 0 to 0, 2 to 0, 0 to 1, 1 to 1, 2 to 1),
    )

    val ALL: List<Polyomino> = SMALL + MEDIUM + LARGE
}
