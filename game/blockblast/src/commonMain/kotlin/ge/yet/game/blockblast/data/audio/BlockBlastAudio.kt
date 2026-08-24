package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.domain.repository.AudioRepository

internal interface BlockBlastAudioPlayer {
    fun playFeedback(type: FeedbackType)
    fun startMusic()
    fun stopMusic()
}

internal class DefaultBlockBlastAudioPlayer(
    private val audio: AudioRepository,
) : BlockBlastAudioPlayer {
    override fun playFeedback(type: FeedbackType) {
        audio.playSound(BlockBlastAudioAssets.voice(type))
    }

    override fun startMusic() {
        audio.startMusic(BlockBlastAudioAssets.music)
    }

    override fun stopMusic() {
        audio.stopMusic()
    }
}
