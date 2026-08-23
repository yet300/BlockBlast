package ge.yet.game.pattern

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PatternQueryTest {
    @Test
    fun `time arcs are half open and intersect exactly`() {
        val arc = TimeArc(CycleTime.of(1, 4), CycleTime.of(3, 4))

        assertTrue(CycleTime.of(1, 4) in arc)
        assertTrue(CycleTime.of(1, 2) in arc)
        assertFalse(CycleTime.of(3, 4) in arc)
        assertEquals(
            TimeArc(CycleTime.of(1, 2), CycleTime.of(3, 4)),
            arc.intersection(TimeArc(CycleTime.of(1, 2), CycleTime.ONE)),
        )
        assertEquals(null, arc.intersection(TimeArc(CycleTime.ONE, CycleTime.of(2, 1))))
    }

    @Test
    fun `pattern events require an active arc inside the whole arc`() {
        val whole = TimeArc(CycleTime.ZERO, CycleTime.ONE)
        val active = TimeArc(CycleTime.of(1, 4), CycleTime.of(3, 4))

        assertEquals(active, PatternEvent(whole, active, "pulse").active)
        assertFailsWith<IllegalArgumentException> {
            PatternEvent(whole, TimeArc(CycleTime.of(-1, 4), CycleTime.of(1, 4)), "pulse")
        }
    }

    @Test
    fun `nested queries share one operation and event budget`() {
        val leaf = pattern<String> { arc, budget ->
            budget.consumeOperation()
            budget.consumeEvents(1)
            listOf(PatternEvent(arc, arc, "pulse"))
        }
        val parent = pattern<String> { arc, budget ->
            budget.consumeOperation()
            leaf.query(arc, budget)
        }
        val budget = PatternQueryBudget(maxOperations = 2, maxEvents = 1)

        assertEquals(listOf("pulse"), parent.query(TimeArc.unit, budget).map { it.value })
        assertEquals(2, budget.operationsUsed)
        assertEquals(1, budget.eventsUsed)
    }

    @Test
    fun `query budget reports the exhausted dimension`() {
        val operationBudget = PatternQueryBudget(maxOperations = 1, maxEvents = 2)
        operationBudget.consumeOperation()
        val operationFailure = assertFailsWith<PatternQueryException> {
            operationBudget.consumeOperation()
        }
        assertEquals(PatternQueryLimit.OPERATIONS, operationFailure.limit)

        val eventBudget = PatternQueryBudget(maxOperations = 2, maxEvents = 1)
        val eventFailure = assertFailsWith<PatternQueryException> {
            eventBudget.consumeEvents(2)
        }
        assertEquals(PatternQueryLimit.EVENTS, eventFailure.limit)
        assertEquals(0, eventBudget.eventsUsed)
    }

    @Test
    fun `invalid arcs and budgets fail before evaluation`() {
        assertFailsWith<IllegalArgumentException> {
            TimeArc(CycleTime.ONE, CycleTime.ZERO)
        }
        assertFailsWith<IllegalArgumentException> { PatternQueryBudget(maxOperations = 0) }
        assertFailsWith<IllegalArgumentException> { PatternQueryBudget(maxEvents = 0) }
    }
}
