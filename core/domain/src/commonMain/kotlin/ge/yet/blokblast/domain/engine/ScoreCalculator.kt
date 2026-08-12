package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.Polyomino

/** Pure scoring logic — fully unit-testable, no state. */
class ScoreCalculator {

    /** 1 point per block inside the polyomino. */
    fun placementPoints(shape: Polyomino): Long = shape.size.toLong()

    /**
     * Clearing reward. The first clear has [comboLevel] 1. Simultaneous
     * clears follow 10, 20, 60, 120... and the active combo level is a
     * linear multiplier. Non-positive combo levels defensively use 1.
     */
    fun clearPoints(linesCount: Int, comboLevel: Int): Long {
        if (linesCount <= 0) return 0L
        val base = if (linesCount == 1) {
            BASE_LINE_REWARD.toLong()
        } else {
            BASE_LINE_REWARD.toLong() * linesCount * (linesCount - 1)
        }
        return base * comboLevel.coerceAtLeast(1)
    }

    fun allClearBonus(linesCount: Int, isBoardEmpty: Boolean): Long =
        if (linesCount > 0 && isBoardEmpty) ALL_CLEAR_BONUS.toLong() else 0L

    companion object {
        const val BASE_LINE_REWARD = 10
        const val ALL_CLEAR_BONUS = 300
    }
}
