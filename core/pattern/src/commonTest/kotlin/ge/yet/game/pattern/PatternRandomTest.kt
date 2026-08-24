package ge.yet.game.pattern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PatternRandomTest {
    @Test
    fun `choose is deterministic and independent of query chunking`() {
        val pattern = choose(seed = 42, values = listOf("a", "b", "c"))
        val whole = pattern.query(TimeArc(CycleTime.ZERO, CycleTime.of(16)))
        val chunks = (0L until 16L).flatMap { cycle ->
            pattern.query(TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1)))
        }

        assertEquals(whole, chunks)
        assertEquals(whole, choose(seed = 42, values = listOf("a", "b", "c")).query(TimeArc(CycleTime.ZERO, CycleTime.of(16))))
        assertNotEquals(whole.map { it.value }, choose(seed = 7, values = listOf("a", "b", "c")).query(TimeArc(CycleTime.ZERO, CycleTime.of(16))).map { it.value })
    }

    @Test
    fun `degrade keeps deterministic whole events across partial queries`() {
        val pattern = sequence(0, 1, 2, 3, 4, 5, 6, 7).degrade(probability = 0.5f, seed = 99)
        val whole = pattern.query(TimeArc.unit)
        val chunks = listOf(
            TimeArc(CycleTime.ZERO, CycleTime.of(1, 2)),
            TimeArc(CycleTime.of(1, 2), CycleTime.ONE),
        ).flatMap(pattern::query)

        assertEquals(whole, chunks)
        assertTrue(whole.size in 1..7)
        assertEquals(8, sequence(0, 1, 2, 3, 4, 5, 6, 7).degrade(0f, 99).query(TimeArc.unit).size)
        assertEquals(0, sequence(0, 1, 2, 3, 4, 5, 6, 7).degrade(1f, 99).query(TimeArc.unit).size)
    }
}
