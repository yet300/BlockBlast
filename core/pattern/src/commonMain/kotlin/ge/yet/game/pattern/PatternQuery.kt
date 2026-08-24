package ge.yet.game.pattern

enum class PatternQueryLimit {
    OPERATIONS,
    EVENTS,
}

class PatternQueryException(
    val limit: PatternQueryLimit,
    maximum: Int,
) : IllegalStateException("Pattern query exceeded the $limit limit of $maximum")

class PatternQueryBudget(
    val maxOperations: Int = DEFAULT_MAX_OPERATIONS,
    val maxEvents: Int = DEFAULT_MAX_EVENTS,
) {
    var operationsUsed: Int = 0
        private set
    var eventsUsed: Int = 0
        private set

    init {
        require(maxOperations > 0) { "maxOperations must be positive" }
        require(maxEvents > 0) { "maxEvents must be positive" }
    }

    internal fun consumeOperation() {
        if (operationsUsed >= maxOperations) {
            throw PatternQueryException(PatternQueryLimit.OPERATIONS, maxOperations)
        }
        operationsUsed += 1
    }

    internal fun consumeEvents(count: Int) {
        require(count >= 0) { "An event count must not be negative" }
        if (count > maxEvents - eventsUsed) {
            throw PatternQueryException(PatternQueryLimit.EVENTS, maxEvents)
        }
        eventsUsed += count
    }

    fun reset() {
        operationsUsed = 0
        eventsUsed = 0
    }

    companion object {
        const val DEFAULT_MAX_OPERATIONS: Int = 4_096
        const val DEFAULT_MAX_EVENTS: Int = 256
    }
}
