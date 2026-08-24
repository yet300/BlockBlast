package ge.yet.game.pattern

fun <T> Pattern<T>.shift(amount: CycleTime): Pattern<T> = streamingPattern { arc, budget, output ->
    budget.consumeOperation()
    val sourceArc = TimeArc(arc.start - amount, arc.endExclusive - amount)
    val first = output.size
    queryInto(sourceArc, budget, output)
    for (index in first until output.size) {
        output.replaceTimes(
            index = index,
            wholeStart = output.wholeStartAt(index) + amount,
            wholeEndExclusive = output.wholeEndExclusiveAt(index) + amount,
            activeStart = output.activeStartAt(index) + amount,
            activeEndExclusive = output.activeEndExclusiveAt(index) + amount,
        )
    }
}

fun <T> Pattern<T>.slow(factor: Int): Pattern<T> = slow(CycleTime.of(factor.toLong()))

fun <T> Pattern<T>.slow(factor: CycleTime): Pattern<T> {
    require(factor > CycleTime.ZERO) { "A slow factor must be positive" }
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        val sourceArc = TimeArc(arc.start / factor, arc.endExclusive / factor)
        val first = output.size
        queryInto(sourceArc, budget, output)
        for (index in first until output.size) {
            output.replaceTimes(
                index = index,
                wholeStart = output.wholeStartAt(index) * factor,
                wholeEndExclusive = output.wholeEndExclusiveAt(index) * factor,
                activeStart = output.activeStartAt(index) * factor,
                activeEndExclusive = output.activeEndExclusiveAt(index) * factor,
            )
        }
    }
}

fun <T> Pattern<T>.fast(factor: Int): Pattern<T> = fast(CycleTime.of(factor.toLong()))

fun <T> Pattern<T>.fast(factor: CycleTime): Pattern<T> {
    require(factor > CycleTime.ZERO) { "A fast factor must be positive" }
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        val sourceArc = TimeArc(arc.start * factor, arc.endExclusive * factor)
        val first = output.size
        queryInto(sourceArc, budget, output)
        for (index in first until output.size) {
            output.replaceTimes(
                index = index,
                wholeStart = output.wholeStartAt(index) / factor,
                wholeEndExclusive = output.wholeEndExclusiveAt(index) / factor,
                activeStart = output.activeStartAt(index) / factor,
                activeEndExclusive = output.activeEndExclusiveAt(index) / factor,
            )
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
    return streamingPattern { arc, budget, output ->
        budget.consumeOperation()
        forEachOverlappingCycle(arc) { cycle ->
            val cycleArc = TimeArc(CycleTime.of(cycle), CycleTime.of(cycle + 1L))
            val selected = if (floorMod(cycle, cycles.toLong()) == cycles.toLong() - 1L) transformed else this
            cycleArc.intersection(arc)?.let { selected.queryInto(it, budget, output) }
        }
    }
}

internal fun floorMod(value: Long, divisor: Long): Long {
    val remainder = value % divisor
    return if (remainder < 0L) remainder + divisor else remainder
}
