package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.Polyomino
import ge.yet.blokblast.domain.model.Position
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
    fun clearPoints_one_line_no_combo() {
        // base=10*1, simultaneous=1, combo=1.0 -> 10
        assertEquals(10L, calc.clearPoints(1, 0))
    }

    @Test
    fun clearPoints_two_lines_no_combo() {
        // base=20, simultaneous=2, combo=1.0 -> 40
        assertEquals(40L, calc.clearPoints(2, 0))
    }

    @Test
    fun clearPoints_three_lines_no_combo() {
        // base=30, simultaneous=3, combo=1.0 -> 90
        assertEquals(90L, calc.clearPoints(3, 0))
    }

    @Test
    fun clearPoints_one_line_combo_two() {
        // base=10, simultaneous=1, combo=2.0 -> 20
        assertEquals(20L, calc.clearPoints(1, 2))
    }

    @Test
    fun clearPoints_two_lines_combo_three() {
        // base=20, simultaneous=2, combo=2.5 -> 40*2.5 = 100
        assertEquals(100L, calc.clearPoints(2, 3))
    }

    @Test
    fun clearPoints_negative_combo_treated_as_no_combo() {
        assertEquals(10L, calc.clearPoints(1, -1))
        assertEquals(40L, calc.clearPoints(2, -5))
    }

    @Test
    fun clearPoints_four_lines_combo_one_excellent_case() {
        // base=40, simultaneous=4, combo=1.5 -> 160*1.5 = 240
        assertEquals(240L, calc.clearPoints(4, 1))
    }

    @Test
    fun base_line_reward_is_ten() {
        assertEquals(10, ScoreCalculator.BASE_LINE_REWARD)
    }
}
