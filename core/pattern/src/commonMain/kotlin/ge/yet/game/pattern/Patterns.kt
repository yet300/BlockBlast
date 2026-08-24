package ge.yet.game.pattern

fun <T> pure(value: T): Pattern<T> = streamingPattern { arc, budget, output ->
    budget.consumeOperation()
    if (!arc.isEmpty) {
        forEachOverlappingCycle(arc) { cycle ->
            val whole = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1L))
            whole.intersection(arc)?.let { active ->
                budget.consumeEvents(1)
                output.append(whole, active, value)
            }
        }
    }
}

fun <T> sequence(vararg values: T): Pattern<T> = sequence(values.asList())

fun <T> sequence(values: List<T>): Pattern<T> {
    require(values.isNotEmpty()) { "A sequence pattern requires at least one value" }
    val snapshot = values.toList()
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        if (!arc.isEmpty) {
            forEachOverlappingCycle(arc) { cycle ->
                snapshot.forEachIndexed { index, value ->
                    val whole = TimeArc(
                        start = CycleTime.of(cycle) + CycleTime.of(index.toLong(), snapshot.size.toLong()),
                        endExclusive = CycleTime.of(cycle) + CycleTime.of(index.toLong() + 1, snapshot.size.toLong()),
                    )
                    whole.intersection(arc)?.let { active ->
                        budget.consumeEvents(1)
                        output.append(whole, active, value)
                    }
                }
            }
        }
    }
}

fun <T> stack(vararg patterns: Pattern<T>): Pattern<T> {
    require(patterns.isNotEmpty()) { "A stack pattern requires at least one child" }
    val snapshot = patterns.toList()
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        val first = output.size
        for (index in snapshot.indices) snapshot[index].queryInto(arc, budget, output)
        output.stableSortByActiveStart(first)
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
