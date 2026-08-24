package ge.yet.game.pattern

/** A half-open interval in cycle time: [start, endExclusive). */
data class TimeArc(
    val start: CycleTime,
    val endExclusive: CycleTime,
) {
    init {
        require(start <= endExclusive) { "A time arc must not end before it starts" }
    }

    val isEmpty: Boolean get() = start == endExclusive

    operator fun contains(time: CycleTime): Boolean = time >= start && time < endExclusive

    fun intersection(other: TimeArc): TimeArc? {
        val intersectionStart = maxOf(start, other.start)
        val intersectionEnd = minOf(endExclusive, other.endExclusive)
        return if (intersectionStart < intersectionEnd) {
            TimeArc(intersectionStart, intersectionEnd)
        } else {
            null
        }
    }

    companion object {
        val unit: TimeArc = TimeArc(CycleTime.ZERO, CycleTime.ONE)
    }
}
