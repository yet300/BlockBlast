package ge.yet.game.pattern

/** A pure temporal value queried over an exact half-open [TimeArc]. */
sealed interface Pattern<out T> {
    fun query(arc: TimeArc, budget: PatternQueryBudget): List<PatternEvent<T>>

    fun queryInto(
        arc: TimeArc,
        budget: PatternQueryBudget,
        output: PatternEventBuffer<@UnsafeVariance T>,
    ) {
        output.recordFallbackQuery()
        val events = query(arc, budget)
        for (index in events.indices) {
            val event = events[index]
            output.append(event.whole, event.active, event.value)
        }
    }
}

fun <T> Pattern<T>.query(arc: TimeArc): List<PatternEvent<T>> =
    query(arc, PatternQueryBudget())

internal fun <T> pattern(
    query: (TimeArc, PatternQueryBudget) -> List<PatternEvent<T>>,
): Pattern<T> = FunctionalPattern(query)

internal fun <T> streamingPattern(
    query: (TimeArc, PatternQueryBudget, PatternEventBuffer<T>) -> Unit,
): Pattern<T> = StreamingPattern(query)

private class FunctionalPattern<T>(
    private val queryBlock: (TimeArc, PatternQueryBudget) -> List<PatternEvent<T>>,
) : Pattern<T> {
    override fun query(arc: TimeArc, budget: PatternQueryBudget): List<PatternEvent<T>> =
        queryBlock(arc, budget)
}

private class StreamingPattern<T>(
    private val queryBlock: (TimeArc, PatternQueryBudget, PatternEventBuffer<T>) -> Unit,
) : Pattern<T> {
    override fun query(arc: TimeArc, budget: PatternQueryBudget): List<PatternEvent<T>> {
        val output = PatternEventBuffer<T>(budget.maxEvents)
        queryBlock(arc, budget, output)
        return output.toEventList()
    }

    override fun queryInto(arc: TimeArc, budget: PatternQueryBudget, output: PatternEventBuffer<T>) {
        queryBlock(arc, budget, output)
    }
}
