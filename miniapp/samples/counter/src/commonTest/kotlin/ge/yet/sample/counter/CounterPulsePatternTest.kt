package ge.yet.sample.counter

import ge.yet.game.pattern.CycleTime
import ge.yet.game.pattern.TimeArc
import ge.yet.game.pattern.query
import ge.yet.game.pattern.sequence
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterPulsePatternTest {
    @Test
    fun `generic pattern schedules counter highlights without audio concepts`() {
        val highlights = sequence(0, 1, 2, 3).query(TimeArc.unit)

        assertEquals(listOf(0, 1, 2, 3), highlights.map { it.value })
        assertEquals(CycleTime.of(1, 4), highlights[1].whole.start)
    }
}
