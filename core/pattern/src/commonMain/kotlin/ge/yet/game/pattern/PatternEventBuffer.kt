package ge.yet.game.pattern

/** Fixed-capacity caller-owned storage for allocation-sensitive pattern queries. */
class PatternEventBuffer<T>(val capacity: Int = PatternQueryBudget.DEFAULT_MAX_EVENTS) {
    private val wholeStartNumerators = LongArray(capacity)
    private val wholeStartDenominators = LongArray(capacity)
    private val wholeEndNumerators = LongArray(capacity)
    private val wholeEndDenominators = LongArray(capacity)
    private val activeStartNumerators = LongArray(capacity)
    private val activeStartDenominators = LongArray(capacity)
    private val activeEndNumerators = LongArray(capacity)
    private val activeEndDenominators = LongArray(capacity)
    private val values = arrayOfNulls<Any?>(capacity)

    var size: Int = 0
        private set

    var fallbackQueriesUsed: Int = 0
        private set

    init {
        require(capacity > 0) { "Pattern event buffer capacity must be positive" }
    }

    fun clear() {
        for (index in 0 until size) values[index] = null
        size = 0
        fallbackQueriesUsed = 0
    }

    @Suppress("UNCHECKED_CAST")
    fun valueAt(index: Int): T {
        require(index in 0 until size)
        return values[index] as T
    }

    fun wholeStartAt(index: Int): CycleTime {
        require(index in 0 until size)
        return CycleTime.of(wholeStartNumerators[index], wholeStartDenominators[index])
    }

    fun wholeEndExclusiveAt(index: Int): CycleTime {
        require(index in 0 until size)
        return CycleTime.of(wholeEndNumerators[index], wholeEndDenominators[index])
    }

    fun activeStartAt(index: Int): CycleTime {
        require(index in 0 until size)
        return CycleTime.of(activeStartNumerators[index], activeStartDenominators[index])
    }

    fun activeEndExclusiveAt(index: Int): CycleTime {
        require(index in 0 until size)
        return CycleTime.of(activeEndNumerators[index], activeEndDenominators[index])
    }

    internal fun append(whole: TimeArc, active: TimeArc, value: T) {
        check(size < capacity) { "Pattern event buffer capacity exceeded" }
        val index = size
        wholeStartNumerators[index] = whole.start.numerator
        wholeStartDenominators[index] = whole.start.denominator
        wholeEndNumerators[index] = whole.endExclusive.numerator
        wholeEndDenominators[index] = whole.endExclusive.denominator
        activeStartNumerators[index] = active.start.numerator
        activeStartDenominators[index] = active.start.denominator
        activeEndNumerators[index] = active.endExclusive.numerator
        activeEndDenominators[index] = active.endExclusive.denominator
        values[index] = value
        size += 1
    }

    internal fun recordFallbackQuery() {
        fallbackQueriesUsed += 1
    }

    internal fun replaceTimes(
        index: Int,
        wholeStart: CycleTime,
        wholeEndExclusive: CycleTime,
        activeStart: CycleTime,
        activeEndExclusive: CycleTime,
    ) {
        require(index in 0 until size)
        wholeStartNumerators[index] = wholeStart.numerator
        wholeStartDenominators[index] = wholeStart.denominator
        wholeEndNumerators[index] = wholeEndExclusive.numerator
        wholeEndDenominators[index] = wholeEndExclusive.denominator
        activeStartNumerators[index] = activeStart.numerator
        activeStartDenominators[index] = activeStart.denominator
        activeEndNumerators[index] = activeEndExclusive.numerator
        activeEndDenominators[index] = activeEndExclusive.denominator
    }

    internal fun copyEvent(from: Int, to: Int) {
        require(from in 0 until size && to in 0 until size)
        if (from == to) return
        wholeStartNumerators[to] = wholeStartNumerators[from]
        wholeStartDenominators[to] = wholeStartDenominators[from]
        wholeEndNumerators[to] = wholeEndNumerators[from]
        wholeEndDenominators[to] = wholeEndDenominators[from]
        activeStartNumerators[to] = activeStartNumerators[from]
        activeStartDenominators[to] = activeStartDenominators[from]
        activeEndNumerators[to] = activeEndNumerators[from]
        activeEndDenominators[to] = activeEndDenominators[from]
        values[to] = values[from]
    }

    internal fun truncate(newSize: Int) {
        require(newSize in 0..size)
        for (index in newSize until size) values[index] = null
        size = newSize
    }

    internal fun stableSortByActiveStart(fromIndex: Int) {
        require(fromIndex in 0..size)
        for (index in fromIndex + 1 until size) {
            val wholeStartNumerator = wholeStartNumerators[index]
            val wholeStartDenominator = wholeStartDenominators[index]
            val wholeEndNumerator = wholeEndNumerators[index]
            val wholeEndDenominator = wholeEndDenominators[index]
            val activeStartNumerator = activeStartNumerators[index]
            val activeStartDenominator = activeStartDenominators[index]
            val activeEndNumerator = activeEndNumerators[index]
            val activeEndDenominator = activeEndDenominators[index]
            val value = values[index]
            val activeStart = CycleTime.of(activeStartNumerator, activeStartDenominator)
            var insertion = index
            while (insertion > fromIndex && activeStart < activeStartAt(insertion - 1)) {
                copyEvent(insertion - 1, insertion)
                insertion -= 1
            }
            wholeStartNumerators[insertion] = wholeStartNumerator
            wholeStartDenominators[insertion] = wholeStartDenominator
            wholeEndNumerators[insertion] = wholeEndNumerator
            wholeEndDenominators[insertion] = wholeEndDenominator
            activeStartNumerators[insertion] = activeStartNumerator
            activeStartDenominators[insertion] = activeStartDenominator
            activeEndNumerators[insertion] = activeEndNumerator
            activeEndDenominators[insertion] = activeEndDenominator
            values[insertion] = value
        }
    }

    internal fun toEventList(): List<PatternEvent<T>> = List(size) { index ->
        PatternEvent(
            whole = TimeArc(wholeStartAt(index), wholeEndExclusiveAt(index)),
            active = TimeArc(activeStartAt(index), activeEndExclusiveAt(index)),
            value = valueAt(index),
        )
    }
}
