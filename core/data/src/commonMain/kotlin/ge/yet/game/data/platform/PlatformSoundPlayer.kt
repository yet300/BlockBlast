package ge.yet.game.data.platform

import ge.yet.game.domain.model.FeedbackType

internal interface PlatformSoundPlayer {
    fun playVoiceFeedback(type: FeedbackType)
    fun startMusic()
    fun stopMusic()
    fun release()
}