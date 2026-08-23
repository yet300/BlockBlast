package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.presets.DeepSpace
import ge.yet.game.miniapp.audio.presets.Explosion
import ge.yet.game.miniapp.audio.presets.PlacementClick
import ge.yet.game.miniapp.audio.presets.PowerUp
import ge.yet.game.miniapp.audio.presets.SuccessSweep

internal object BlockBlastAudio {
    val program = audioProgram {
        tempo(92f)
        include(DeepSpace(name = "blockblast_space", seed = 2_026_08_23L, gain = 0.38f))
        include(PlacementClick(name = "feedback_good", gain = 0.3f))
        include(SuccessSweep(name = "feedback_great", gain = 0.38f))
        include(PowerUp(name = "feedback_amazing", gain = 0.42f))
        include(SuccessSweep(name = "feedback_excellent", gain = 0.52f))
        include(Explosion(name = "feedback_unbelievable", seed = 64L, gain = 0.48f))
    }

    fun sfxName(type: FeedbackType): SfxName = SfxName("feedback_${type.name.lowercase()}")
}

internal interface BlockBlastAudioPlayer {
    fun playFeedback(type: FeedbackType)
    fun startMusic()
    fun stopMusic()
}

internal class DefaultBlockBlastAudioPlayer(
    private val audio: MiniAppAudio,
) : BlockBlastAudioPlayer {
    override fun playFeedback(type: FeedbackType) {
        audio.playSfx(BlockBlastAudio.program, BlockBlastAudio.sfxName(type))
    }

    override fun startMusic() {
        audio.playMusic(BlockBlastAudio.program)
    }

    override fun stopMusic() {
        audio.stopMusic()
    }
}
