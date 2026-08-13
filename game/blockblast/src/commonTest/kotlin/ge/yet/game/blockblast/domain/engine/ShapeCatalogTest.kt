package ge.yet.game.blockblast.domain.engine

import ge.yet.game.blockblast.domain.model.Position
import ge.yet.game.blockblast.domain.engine.ShapeCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShapeCatalogTest {

    @Test
    fun catalog_contains_true_single_cell_piece() {
        assertEquals(listOf(Position(0, 0)), ShapeCatalog.SINGLE.cells)
    }

    @Test
    fun catalog_excludes_disconnected_diagonal_shapes() {
        val ids = ShapeCatalog.ALL.mapTo(mutableSetOf()) { it.id }

        assertFalse("diag2_tlbr" in ids)
        assertFalse("diag2_trbl" in ids)
        assertFalse("diag3_tlbr" in ids)
        assertFalse("diag3_trbl" in ids)
    }

    @Test
    fun every_catalog_shape_is_orthogonally_connected() {
        ShapeCatalog.ALL.forEach { shape ->
            val cells = shape.cells.toSet()
            val reachable = mutableSetOf(cells.first())
            val frontier = ArrayDeque<Position>().apply { add(cells.first()) }

            while (frontier.isNotEmpty()) {
                val cell = frontier.removeFirst()
                val neighbors = listOf(
                    Position(cell.x - 1, cell.y),
                    Position(cell.x + 1, cell.y),
                    Position(cell.x, cell.y - 1),
                    Position(cell.x, cell.y + 1),
                )
                neighbors
                    .filter { it in cells && reachable.add(it) }
                    .forEach(frontier::addLast)
            }

            assertEquals(
                cells,
                reachable,
                "${shape.id} must be one orthogonally connected polyomino",
            )
            assertEquals(
                shape.cells.size,
                cells.size,
                "${shape.id} must not contain duplicate cells",
            )
            assertTrue(shape.cells.minOf { it.x } == 0, "${shape.id} must be normalized on x")
            assertTrue(shape.cells.minOf { it.y } == 0, "${shape.id} must be normalized on y")
        }
    }
}
