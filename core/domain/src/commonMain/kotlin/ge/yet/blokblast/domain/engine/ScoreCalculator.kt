package ge.yet.blokblast.domain.engine

import dev.zacsweers.metro.Inject
import ge.yet.blokblast.domain.model.Polyomino

/** Pure scoring logic — fully unit-testable, no state. */
@Inject
class ScoreCalculator {

    /** 1 point per block inside the polyomino. */
    fun placementPoints(shape: Polyomino): Long = shape.size.toLong()

    /**
     * Clearing reward.
     *  - base = 10 * linesCount
     *  - simultaneous-clear multiplier: x1 / x2 / x3 / x4...
     *  - combo multiplier: 1 + combo * 0.5  (combo>=1)
     */
    fun clearPoints(linesCount: Int, comboLevel: Int): Long {
        if (linesCount <= 0) return 0L
        val base = (BASE_LINE_REWARD * linesCount).toLong()
        val simultaneousMultiplier = linesCount.toLong()
        val comboMultiplier = if (comboLevel <= 0) 1.0 else 1.0 + comboLevel * 0.5
        return ((base * simultaneousMultiplier) * comboMultiplier).toLong()
    }

    companion object {
        const val BASE_LINE_REWARD = 10
    }
}
