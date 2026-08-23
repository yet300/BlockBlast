package ge.yet.game.pattern

fun <T> pure(value: T): Pattern<T> = pattern { arc, budget ->
    budget.consumeOperation()
    buildCycleEvents(arc, budget) { whole, active -> PatternEvent(whole, active, value) }
}

fun <T> sequence(vararg values: T): Pattern<T> = sequence(values.asList())

fun <T> sequence(values: List<T>): Pattern<T> {
    require(values.isNotEmpty()) { "A sequence pattern requires at least one value" }
    val snapshot = values.toList()
    return pattern { arc, budget ->
        budget.consumeOperation()
        if (arc.isEmpty) {
            emptyList()
        } else {
            val events = mutableListOf<PatternEvent<T>>()
            forEachOverlappingCycle(arc) { cycle ->
                snapshot.forEachIndexed { index, value ->
                    val whole = TimeArc(
                        start = CycleTime.of(cycle) + CycleTime.of(index.toLong(), snapshot.size.toLong()),
                        endExclusive = CycleTime.of(cycle) + CycleTime.of(index.toLong() + 1, snapshot.size.toLong()),
                    )
                    whole.intersection(arc)?.let { active ->
                        budget.consumeEvents(1)
                        events += PatternEvent(whole, active, value)
                    }
                }
            }
            events
        }
    }
}

fun <T> stack(vararg patterns: Pattern<T>): Pattern<T> {
    require(patterns.isNotEmpty()) { "A stack pattern requires at least one child" }
    val snapshot = patterns.toList()
    return pattern { arc, budget ->
        budget.consumeOperation()
        snapshot.flatMapIndexed { patternIndex, child ->
            child.query(arc, budget).mapIndexed { eventIndex, event ->
                OrderedPatternEvent(event, patternIndex, eventIndex)
            }
        }.sortedWith(
            compareBy<OrderedPatternEvent<T>>(
                { it.event.active.start },
                { it.patternIndex },
                { it.eventIndex },
            ),
        ).map(OrderedPatternEvent<T>::event)
    }
}

internal fun CycleTime.floorToLong(): Long {
    val quotient = numerator / denominator
    val remainder = numerator % denominator
    return if (remainder < 0L) quotient - 1L else quotient
}

internal inline fun forEachOverlappingCycle(arc: TimeArc, block: (Long) -> Unit) {
    if (arc.isEmpty) return
    var cycle = arc.start.floorToLong()
    while (CycleTime.of(cycle) < arc.endExclusive) {
        block(cycle)
        if (cycle == Long.MAX_VALUE) return
        cycle += 1L
    }
}

private inline fun <T> buildCycleEvents(
    arc: TimeArc,
    budget: PatternQueryBudget,
    event: (whole: TimeArc, active: TimeArc) -> PatternEvent<T>,
): List<PatternEvent<T>> {
    if (arc.isEmpty) return emptyList()
    val events = mutableListOf<PatternEvent<T>>()
    forEachOverlappingCycle(arc) { cycle ->
        val whole = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1L))
        whole.intersection(arc)?.let { active ->
            budget.consumeEvents(1)
            events += event(whole, active)
        }
    }
    return events
}

private data class OrderedPatternEvent<T>(
    val event: PatternEvent<T>,
    val patternIndex: Int,
    val eventIndex: Int,
)
