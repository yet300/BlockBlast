package ge.yet.game.pattern

fun <T> Pattern<T>.shift(amount: CycleTime): Pattern<T> = pattern { arc, budget ->
    budget.consumeOperation()
    val sourceArc = TimeArc(arc.start - amount, arc.endExclusive - amount)
    query(sourceArc, budget).map { event ->
        event.transformTime { it + amount }
    }
}

fun <T> Pattern<T>.slow(factor: Int): Pattern<T> = slow(CycleTime.of(factor.toLong()))

fun <T> Pattern<T>.slow(factor: CycleTime): Pattern<T> {
    require(factor > CycleTime.ZERO) { "A slow factor must be positive" }
    return pattern { arc, budget ->
        budget.consumeOperation()
        val sourceArc = TimeArc(arc.start / factor, arc.endExclusive / factor)
        query(sourceArc, budget).map { event ->
            event.transformTime { it * factor }
        }
    }
}

fun <T> Pattern<T>.fast(factor: Int): Pattern<T> = fast(CycleTime.of(factor.toLong()))

fun <T> Pattern<T>.fast(factor: CycleTime): Pattern<T> {
    require(factor > CycleTime.ZERO) { "A fast factor must be positive" }
    return pattern { arc, budget ->
        budget.consumeOperation()
        val sourceArc = TimeArc(arc.start * factor, arc.endExclusive * factor)
        query(sourceArc, budget).map { event ->
            event.transformTime { it / factor }
        }
    }
}

fun <T> Pattern<T>.repeat(times: Int): Pattern<T> {
    require(times > 0) { "A repeat count must be positive" }
    return fast(times)
}

fun <T> Pattern<T>.every(
    cycles: Int,
    transform: (Pattern<T>) -> Pattern<T>,
): Pattern<T> {
    require(cycles > 0) { "An every-cycle interval must be positive" }
    val transformed = transform(this)
    return pattern { arc, budget ->
        budget.consumeOperation()
        val events = mutableListOf<PatternEvent<T>>()
        forEachOverlappingCycle(arc) { cycle ->
            val cycleArc = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1L))
            val selected = if (floorMod(cycle, cycles.toLong()) == cycles.toLong() - 1L) transformed else this
            cycleArc.intersection(arc)?.let { events += selected.query(it, budget) }
        }
        events
    }
}

private fun <T> PatternEvent<T>.transformTime(transform: (CycleTime) -> CycleTime): PatternEvent<T> =
    PatternEvent(
        whole = TimeArc(transform(whole.start), transform(whole.endExclusive)),
        active = TimeArc(transform(active.start), transform(active.endExclusive)),
        value = value,
    )

internal fun floorMod(value: Long, divisor: Long): Long {
    val remainder = value % divisor
    return if (remainder < 0L) remainder + divisor else remainder
}
