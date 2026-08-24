package ge.yet.game.pattern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EuclideanPatternTest {
    @Test
    fun `euclidean rhythm distributes pulses over exact steps`() {
        val events = euclidean(value = "hit", pulses = 3, steps = 8).query(TimeArc.unit)

        assertEquals(
            listOf(CycleTime.ZERO, CycleTime.of(3, 8), CycleTime.of(6, 8)),
            events.map { it.whole.start },
        )
    }

    @Test
    fun `euclidean rotation is normalized and query chunks stay identical`() {
        val pattern = euclidean(value = 1, pulses = 5, steps = 8, rotation = -1)
        val whole = pattern.query(TimeArc(CycleTime.ZERO, CycleTime.of(2)))
        val chunks = listOf(
            pattern.query(TimeArc.unit),
            pattern.query(TimeArc(CycleTime.ONE, CycleTime.of(2))),
        ).flatten()

        assertEquals(whole, chunks)
    }

    @Test
    fun `euclidean rhythm rejects invalid pulse and step counts`() {
        assertFailsWith<IllegalArgumentException> { euclidean("x", pulses = -1, steps = 8) }
        assertFailsWith<IllegalArgumentException> { euclidean("x", pulses = 9, steps = 8) }
        assertFailsWith<IllegalArgumentException> { euclidean("x", pulses = 0, steps = 0) }
    }
}
