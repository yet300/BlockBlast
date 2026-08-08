package ge.yet.blokblast.domain.engine

import ge.yet.blokblast.domain.model.VoiceFeedback

/** Returns the single highest-priority voice response for a resolved move. */
fun selectVoiceFeedback(
    linesCount: Int,
    isCrossClear: Boolean,
    isBoardEmpty: Boolean,
    comboLevel: Int,
): VoiceFeedback? {
    if (linesCount <= 0) return null

    return when {
        isBoardEmpty -> VoiceFeedback.UNBELIEVABLE
        isCrossClear || linesCount >= 4 -> VoiceFeedback.EXCELLENT
        linesCount == 3 -> VoiceFeedback.GREAT
        linesCount == 2 -> VoiceFeedback.GOOD
        comboLevel == 3 -> VoiceFeedback.AMAZING
        else -> null
    }
}
