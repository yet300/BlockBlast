package ge.yet.game.pattern

/** A pure temporal value queried over an exact half-open [TimeArc]. */
sealed interface Pattern<T> {
    fun query(arc: TimeArc, budget: PatternQueryBudget): List<PatternEvent<T>>
}

fun <T> Pattern<T>.query(arc: TimeArc): List<PatternEvent<T>> =
    query(arc, PatternQueryBudget())

internal fun <T> pattern(
    query: (TimeArc, PatternQueryBudget) -> List<PatternEvent<T>>,
): Pattern<T> = FunctionalPattern(query)

private class FunctionalPattern<T>(
    private val queryBlock: (TimeArc, PatternQueryBudget) -> List<PatternEvent<T>>,
) : Pattern<T> {
    override fun query(arc: TimeArc, budget: PatternQueryBudget): List<PatternEvent<T>> =
        queryBlock(arc, budget)
}
