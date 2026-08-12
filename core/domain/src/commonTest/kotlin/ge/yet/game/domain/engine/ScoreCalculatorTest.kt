package ge.yet.game.domain.engine

import ge.yet.game.domain.model.Polyomino
import ge.yet.game.domain.engine.ScoreCalculator
import ge.yet.game.domain.model.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class ScoreCalculatorTest {

    private val calc = ScoreCalculator()

    private fun shape(size: Int): Polyomino =
        Polyomino(id = "s$size", cells = (0 until size).map { Position(it, 0) })

    @Test
    fun placementPoints_equals_shape_size() {
        assertEquals(1L, calc.placementPoints(shape(1)))
        assertEquals(2L, calc.placementPoints(shape(2)))
        assertEquals(5L, calc.placementPoints(shape(5)))
        assertEquals(9L, calc.placementPoints(shape(9)))
    }

    @Test
    fun clearPoints_zero_lines_is_zero() {
        assertEquals(0L, calc.clearPoints(0, 0))
        assertEquals(0L, calc.clearPoints(0, 5))
        assertEquals(0L, calc.clearPoints(-1, 5))
    }

    @Test
    fun clearPoints_first_clear_uses_approved_base_table() {
        val expected = listOf(10L, 20L, 60L, 120L, 200L, 300L)

        expected.forEachIndexed { index, points ->
            assertEquals(points, calc.clearPoints(linesCount = index + 1, comboLevel = 1))
        }
    }

    @Test
    fun clearPoints_multiplies_base_by_combo_level() {
        assertEquals(20L, calc.clearPoints(linesCount = 1, comboLevel = 2))
        assertEquals(60L, calc.clearPoints(linesCount = 2, comboLevel = 3))
        assertEquals(480L, calc.clearPoints(linesCount = 4, comboLevel = 4))
    }

    @Test
    fun clearPoints_non_positive_combo_uses_first_clear_multiplier() {
        assertEquals(10L, calc.clearPoints(linesCount = 1, comboLevel = 0))
        assertEquals(20L, calc.clearPoints(linesCount = 2, comboLevel = -5))
    }

    @Test
    fun allClearBonus_requires_a_clear_and_empty_resulting_board() {
        assertEquals(0L, calc.allClearBonus(linesCount = 0, isBoardEmpty = true))
        assertEquals(0L, calc.allClearBonus(linesCount = 1, isBoardEmpty = false))
        assertEquals(300L, calc.allClearBonus(linesCount = 1, isBoardEmpty = true))
    }

    @Test
    fun base_line_reward_is_ten() {
        assertEquals(10, ScoreCalculator.BASE_LINE_REWARD)
    }

    @Test
    fun all_clear_bonus_is_three_hundred() {
        assertEquals(300, ScoreCalculator.ALL_CLEAR_BONUS)
    }
}
