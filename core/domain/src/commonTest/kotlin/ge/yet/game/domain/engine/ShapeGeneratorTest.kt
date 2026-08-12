package ge.yet.game.domain.engine

import ge.yet.game.domain.model.Grid
import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.engine.ShapeCatalog
import ge.yet.game.domain.engine.ShapeGenerator
import ge.yet.game.domain.engine.WeightedShapeGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShapeGeneratorTest {

    private val gen = WeightedShapeGenerator()

    @Test
    fun default_factory_creates_the_standard_generator() {
        val expected = WeightedShapeGenerator().nextTray(seed = 42L).map { it.id }

        val actual = ShapeGenerator.default().nextTray(seed = 42L).map { it.id }

        assertEquals(expected, actual)
    }

    @Test
    fun open_board_trays_favor_large_shapes_over_compact_shapes() {
        repeat(50) { seed ->
            val tray = gen.nextTray(grid = Grid(), seed = seed.toLong())

            assertTrue(tray.any { it.size >= 5 }, "tray has no large shape: $tray")
            assertTrue(
                tray.count { it.size <= 2 } <= 1,
                "tray has too many compact shapes: $tray",
            )
        }
    }

    @Test
    fun nearly_full_board_tray_keeps_at_least_one_placeable_shape() {
        val cells = IntArray(Grid.SIZE * Grid.SIZE) { 1 }
        cells[0] = Grid.EMPTY
        val nearlyFullGrid = Grid(cells)

        repeat(30) { seed ->
            val tray = gen.nextTray(grid = nearlyFullGrid, seed = seed.toLong())

            assertTrue(
                tray.any { it.id == ShapeCatalog.SINGLE.id },
                "tray must contain the only shape that fits: $tray",
            )
        }
    }

    @Test
    fun generated_tray_has_three_distinct_shapes() {
        repeat(100) { seed ->
            val tray = gen.nextTray(grid = Grid(), seed = seed.toLong())

            assertEquals(3, tray.map { it.id }.distinct().size, "duplicate tray: $tray")
        }
    }

    @Test
    fun open_board_trays_vary_their_size_mix() {
        val sizeMixes = (0L until 100L).mapTo(mutableSetOf()) { seed ->
            gen.nextTray(grid = Grid(), seed = seed)
                .map { shape ->
                    when (shape.size) {
                        in 1..2 -> "compact"
                        in 3..4 -> "medium"
                        else -> "large"
                    }
                }
                .sorted()
        }

        assertTrue(sizeMixes.size >= 2, "size mix never changes: $sizeMixes")
    }

    @Test
    fun moderately_open_board_still_favors_large_shapes() {
        val cells = IntArray(Grid.SIZE * Grid.SIZE) { Grid.EMPTY }
        listOf(
            0, 2, 4, 6,
            9, 11, 13, 15,
            16, 18, 20, 22,
            25, 27, 29, 31,
        ).forEach { cells[it] = 1 }
        val grid = Grid(cells)

        repeat(50) { seed ->
            val tray = gen.nextTray(grid = grid, seed = seed.toLong())

            assertTrue(tray.any { it.size >= 5 }, "tray has no large shape: $tray")
            assertTrue(tray.count { it.size <= 2 } <= 1, "tray is too compact: $tray")
        }
    }

    @Test
    fun fragmented_open_board_tray_keeps_a_placeable_shape() {
        val cells = IntArray(Grid.SIZE * Grid.SIZE) { Grid.EMPTY }
        for (y in 0 until Grid.SIZE) {
            for (x in 0 until Grid.SIZE) {
                if ((x + y) % 3 == 2) cells[y * Grid.SIZE + x] = 1
            }
        }
        val grid = Grid(cells)

        repeat(100) { seed ->
            val tray = gen.nextTray(grid = grid, seed = seed.toLong())

            assertTrue(tray.any { it.canFit(grid) }, "no shape fits: $tray")
        }
    }

    @Test
    fun nextTray_has_size_three() {
        repeat(20) { seed ->
            assertEquals(3, gen.nextTray(seed.toLong()).size)
        }
    }

    @Test
    fun nextTray_deterministic_for_same_seed() {
        val a = gen.nextTray(seed = 42L).map { it.id }
        val b = gen.nextTray(seed = 42L).map { it.id }
        assertEquals(a, b)
    }

    @Test
    fun smallReviveTray_is_single_horizontal_two_and_vertical_two() {
        val tray = gen.smallReviveTray()

        assertEquals(listOf("single", "h2", "v2"), tray.map { it.id })
        assertEquals(listOf(1, 2, 2), tray.map { it.size })
    }

    @Test
    fun smallReviveTray_is_stable() {
        assertEquals(
            gen.smallReviveTray().map { it.id },
            gen.smallReviveTray().map { it.id },
        )
    }

    @Test
    fun nextTray_all_ids_are_from_catalog() {
        val allIds = ShapeCatalog.ALL.map { it.id }.toSet()
        repeat(100) { seed ->
            for (piece in gen.nextTray(seed.toLong())) {
                assertTrue(piece.id in allIds, "unknown id ${piece.id}")
            }
        }
    }

    private fun Polyomino.canFit(grid: Grid): Boolean {
        for (y in 0 until Grid.SIZE) {
            for (x in 0 until Grid.SIZE) {
                if (cells.all { cell ->
                        val gridX = x + cell.x
                        val gridY = y + cell.y
                        grid.inBounds(gridX, gridY) && grid.isEmpty(gridX, gridY)
                    }
                ) {
                    return true
                }
            }
        }
        return false
    }
}
