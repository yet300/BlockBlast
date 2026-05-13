package ge.yet.blokblast.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GridTest {

    @Test
    fun fresh_grid_is_all_empty() {
        val g = Grid()
        assertTrue(g.isBoardEmpty())
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
            assertTrue(g.isEmpty(x, y))
            assertEquals(Grid.EMPTY, g.colorAt(x, y))
        }
    }

    @Test
    fun inBounds_corners_and_outside() {
        val g = Grid()
        assertTrue(g.inBounds(0, 0))
        assertTrue(g.inBounds(Grid.SIZE - 1, Grid.SIZE - 1))
        assertFalse(g.inBounds(-1, 0))
        assertFalse(g.inBounds(0, -1))
        assertFalse(g.inBounds(Grid.SIZE, 0))
        assertFalse(g.inBounds(0, Grid.SIZE))
    }

    @Test
    fun withCell_does_not_mutate_original() {
        val original = Grid()
        val updated = original.withCell(2, 3, colorId = 5)
        assertTrue(original.isEmpty(2, 3))
        assertFalse(updated.isEmpty(2, 3))
        assertEquals(5, updated.colorAt(2, 3))
        assertNotSame(original, updated)
    }

    @Test
    fun withCells_empty_list_returns_equivalent_grid() {
        val g = Grid()
        val same = g.withCells(emptyList(), colorId = 1)
        assertEquals(g, same)
    }

    @Test
    fun withCells_stamps_all_positions() {
        val cells = listOf(Position(0, 0), Position(1, 0), Position(0, 1))
        val g = Grid().withCells(cells, colorId = 3)
        assertEquals(3, g.colorAt(0, 0))
        assertEquals(3, g.colorAt(1, 0))
        assertEquals(3, g.colorAt(0, 1))
        assertTrue(g.isEmpty(2, 0))
    }

    @Test
    fun clearedAt_empty_set_returns_same_instance() {
        val g = Grid().withCell(0, 0, 1)
        assertSame(g, g.clearedAt(emptySet()))
    }

    @Test
    fun clearedAt_clears_positions() {
        val g = Grid().withCells(listOf(Position(0, 0), Position(1, 1)), 2)
        val cleared = g.clearedAt(setOf(Position(0, 0)))
        assertTrue(cleared.isEmpty(0, 0))
        assertFalse(cleared.isEmpty(1, 1))
    }

    @Test
    fun isBoardEmpty_after_partial_fill_is_false() {
        val g = Grid().withCell(4, 4, 1)
        assertFalse(g.isBoardEmpty())
    }

    @Test
    fun equals_and_hashCode_are_content_based() {
        val a = Grid().withCell(0, 0, 1)
        val b = Grid().withCell(0, 0, 1)
        val c = Grid().withCell(0, 0, 2)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotSame(a, b)
        assertFalse(a == c)
    }

    @Test
    fun fully_filled_grid_is_not_empty() {
        var g = Grid()
        for (y in 0 until Grid.SIZE) for (x in 0 until Grid.SIZE) {
            g = g.withCell(x, y, 1)
        }
        assertFalse(g.isBoardEmpty())
    }
}
