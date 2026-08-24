package ge.yet.game.pattern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CycleTimeTest {
    @Test
    fun `construction normalizes sign and greatest common divisor`() {
        assertEquals(CycleTime.of(1, 2), CycleTime.of(4, 8))
        assertEquals(CycleTime.of(-1, 2), CycleTime.of(1, -2))
        assertEquals(CycleTime.ZERO, CycleTime.of(0, -7))
    }

    @Test
    fun `arithmetic remains exact across different denominators`() {
        assertEquals(CycleTime.of(5, 6), CycleTime.of(1, 2) + CycleTime.of(1, 3))
        assertEquals(CycleTime.of(1, 6), CycleTime.of(1, 2) - CycleTime.of(1, 3))
        assertEquals(CycleTime.of(-3, 10), CycleTime.of(-2, 5) * CycleTime.of(3, 4))
        assertEquals(CycleTime.of(8, 9), CycleTime.of(2, 3) / CycleTime.of(3, 4))
    }

    @Test
    fun `comparison does not overflow cross products`() {
        val smaller = CycleTime.of(Long.MAX_VALUE - 1, Long.MAX_VALUE)
        val larger = CycleTime.of(Long.MAX_VALUE, Long.MAX_VALUE)

        assertTrue(smaller < larger)
        assertTrue(CycleTime.of(Long.MIN_VALUE, Long.MAX_VALUE) < CycleTime.of(-1, 1))
    }

    @Test
    fun `invalid or unrepresentable values fail explicitly`() {
        assertFailsWith<IllegalArgumentException> { CycleTime.of(1, 0) }
        assertFailsWith<ArithmeticException> { CycleTime.of(Long.MIN_VALUE, -1) }
        assertFailsWith<ArithmeticException> { CycleTime.of(Long.MAX_VALUE, 1) + CycleTime.ONE }
        assertFailsWith<ArithmeticException> { CycleTime.ONE / CycleTime.ZERO }
    }

    @Test
    fun `normalization and ordering stay stable across a bounded adversarial matrix`() {
        for (numerator in -40L..40L) {
            for (denominator in 1L..20L) {
                val value = CycleTime.of(numerator, denominator)
                assertEquals(value, CycleTime.of(numerator * 7L, denominator * 7L))
                assertEquals(numerator < denominator, value < CycleTime.ONE)
                assertEquals(numerator == 0L, value == CycleTime.ZERO)
            }
        }
    }
}
