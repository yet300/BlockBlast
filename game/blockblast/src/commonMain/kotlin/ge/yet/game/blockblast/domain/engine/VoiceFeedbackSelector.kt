package ge.yet.game.blockblast.domain.engine

import ge.yet.game.blockblast.domain.model.FeedbackType

/** Returns the single highest-priority voice response for a resolved move. */
internal fun selectVoiceFeedback(
    linesCount: Int,
    isCrossClear: Boolean,
    isBoardEmpty: Boolean,
    comboLevel: Int,
): FeedbackType? {
    if (linesCount <= 0) return null

    return when {
        isBoardEmpty -> FeedbackType.UNBELIEVABLE
        isCrossClear || linesCount >= 4 -> FeedbackType.EXCELLENT
        linesCount == 3 -> FeedbackType.GREAT
        linesCount == 2 -> FeedbackType.GOOD
        comboLevel == 3 -> FeedbackType.AMAZING
        else -> null
    }
}
