package ge.yet.game.pattern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PatternTransformsTest {
    @Test
    fun `pure repeats once per cycle and clips only the active arc`() {
        val query = TimeArc(CycleTime.of(1, 2), CycleTime.of(3, 2))

        val events = pure("pulse").query(query)

        assertEquals(
            listOf(
                PatternEvent(TimeArc(CycleTime.ZERO, CycleTime.ONE), TimeArc(CycleTime.of(1, 2), CycleTime.ONE), "pulse"),
                PatternEvent(TimeArc(CycleTime.ONE, CycleTime.of(2)), TimeArc(CycleTime.ONE, CycleTime.of(3, 2)), "pulse"),
            ),
            events,
        )
    }

    @Test
    fun `sequence divides every cycle into exact equal steps`() {
        val events = sequence("a", "b", "c", "d").query(TimeArc.unit)

        assertEquals(listOf("a", "b", "c", "d"), events.map { it.value })
        assertEquals(
            listOf(0, 1, 2, 3).map { index ->
                TimeArc(CycleTime.of(index.toLong(), 4), CycleTime.of(index.toLong() + 1, 4))
            },
            events.map { it.whole },
        )
    }

    @Test
    fun `stack preserves time order and declaration order for ties`() {
        val events = stack(sequence("first", "later"), pure("second")).query(TimeArc.unit)

        assertEquals(listOf("first", "second", "later"), events.map { it.value })
    }

    @Test
    fun `shift slow fast and repeat transform exact event time`() {
        assertEquals(
            TimeArc(CycleTime.of(1, 4), CycleTime.of(3, 4)),
            pure("x").shift(CycleTime.of(1, 4))
                .query(TimeArc(CycleTime.of(1, 4), CycleTime.of(3, 4)))
                .single().active,
        )
        assertEquals(
            listOf(TimeArc(CycleTime.ZERO, CycleTime.of(2))),
            pure("x").slow(2).query(TimeArc(CycleTime.ZERO, CycleTime.of(2))).map { it.active },
        )
        assertEquals(4, pure("x").fast(4).query(TimeArc.unit).size)
        assertEquals(3, pure("x").repeat(3).query(TimeArc.unit).size)
    }

    @Test
    fun `every selects a prebuilt transformed pattern for matching cycles`() {
        val base = sequence("a", "b")
        val events = base.every(2) { it.fast(2) }
            .query(TimeArc(CycleTime.ZERO, CycleTime.of(3)))

        assertEquals(4, events.count { it.whole.start >= CycleTime.ONE && it.whole.start < CycleTime.of(2) })
        assertEquals(2, events.count { it.whole.start < CycleTime.ONE })
        assertEquals(2, events.count { it.whole.start >= CycleTime.of(2) })
    }

    @Test
    fun `nested combinators cannot reset the shared operation budget`() {
        val nested = stack(pure("a").shift(CycleTime.ZERO), pure("b"))

        val error = assertFailsWith<PatternQueryException> {
            nested.query(TimeArc.unit, PatternQueryBudget(maxOperations = 2, maxEvents = 4))
        }

        assertEquals(PatternQueryLimit.OPERATIONS, error.limit)
    }
}
