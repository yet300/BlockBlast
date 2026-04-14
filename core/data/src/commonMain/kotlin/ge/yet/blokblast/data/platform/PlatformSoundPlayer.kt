package ge.yet.blokblast.data.platform

import ge.yet.blokblast.domain.model.FeedbackType

internal interface PlatformSoundPlayer {
    fun playPlacement()
    fun playClear(lines: Int)
    fun playVoiceFeedback(type: FeedbackType)
    fun playVoiceCombo(combo: Int)
    fun release()
}