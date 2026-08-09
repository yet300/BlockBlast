package ge.yet.blokblast.data.platform

import ge.yet.blokblast.domain.model.FeedbackType

internal interface PlatformSoundPlayer {
    fun playVoiceFeedback(type: FeedbackType)
    fun startMusic()
    fun stopMusic()
    fun release()
}