package ge.yet.game.pattern

/** One immutable pattern value and the exact interval for which it is active. */
data class PatternEvent<out T>(
    val whole: TimeArc,
    val active: TimeArc,
    val value: T,
) {
    init {
        require(!whole.isEmpty) { "A pattern event whole arc must not be empty" }
        require(!active.isEmpty) { "A pattern event active arc must not be empty" }
        require(active.start >= whole.start && active.endExclusive <= whole.endExclusive) {
            "A pattern event active arc must be contained by its whole arc"
        }
    }
}
