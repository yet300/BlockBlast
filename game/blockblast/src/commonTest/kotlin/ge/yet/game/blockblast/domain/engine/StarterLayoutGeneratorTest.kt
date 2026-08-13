package ge.yet.game.blockblast.domain.engine

import ge.yet.game.blockblast.domain.model.Grid
import ge.yet.game.blockblast.domain.model.Polyomino
import ge.yet.game.blockblast.domain.engine.StarterLayoutGenerator
import ge.yet.game.blockblast.domain.engine.WeightedShapeGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StarterLayoutGeneratorTest {

    private val generator = StarterLayoutGenerator(WeightedShapeGenerator())

    @Test
    fun disabled_generation_always_returns_an_empty_round() {
        repeat(50) { seed ->
            val round = generator.generate(seed = seed.toLong(), enabled = false)

            assertTrue(round.grid.isBoardEmpty())
            assertEquals(3, round.shapes.size)
            assertNull(round.starterLayout)
        }
    }

    @Test
    fun generation_is_deterministic_for_the_same_seed() {
        repeat(100) { seed ->
            val first = generator.generate(seed = seed.toLong(), enabled = true)
            val second = generator.generate(seed = seed.toLong(), enabled = true)

            assertEquals(first, second)
        }
    }

    @Test
    fun enabled_generation_mixes_empty_and_populated_rounds() {
        val rounds = (0L until 1_000L).map { generator.generate(it, enabled = true) }
        val populatedCount = rounds.count { !it.grid.isBoardEmpty() }

        assertTrue(populatedCount in 350..650, "populated=$populatedCount")
    }

    @Test
    fun populated_rounds_are_interesting_and_start_without_complete_lines() {
        val populated = (0L until 1_000L)
            .map { generator.generate(it, enabled = true) }
            .filterNot { it.grid.isBoardEmpty() }

        assertTrue(populated.isNotEmpty())
        populated.forEach { round ->
            val occupied = round.grid.cells.count { it != Grid.EMPTY }
            assertTrue(occupied in 14..22, "occupied=$occupied")
            assertEquals(3, round.shapes.size)
            assertFalse(round.grid.hasCompleteLine(), "starter contains a complete line")
            val metadata = assertNotNull(round.starterLayout)
            assertTrue(metadata.templateId in 1..12)
            assertTrue(metadata.quarterTurns in 0..3)
        }
    }

    @Test
    fun every_initial_shape_can_be_placed_immediately() {
        val populated = (0L until 1_000L)
            .map { generator.generate(it, enabled = true) }
            .filterNot { it.grid.isBoardEmpty() }

        populated.forEach { round ->
            round.shapes.forEach { shape ->
                assertTrue(
                    shape.canFit(round.grid),
                    "${shape.id} cannot be placed on ${round.grid}",
                )
            }
        }
    }

    private fun Grid.hasCompleteLine(): Boolean {
        val fullRow = (0 until Grid.SIZE).any { y ->
            (0 until Grid.SIZE).all { x -> !isEmpty(x, y) }
        }
        val fullColumn = (0 until Grid.SIZE).any { x ->
            (0 until Grid.SIZE).all { y -> !isEmpty(x, y) }
        }
        return fullRow || fullColumn
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
