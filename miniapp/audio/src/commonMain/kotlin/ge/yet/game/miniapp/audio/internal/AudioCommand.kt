package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName

internal sealed interface AudioCommand {
    data class PlayMusic(val program: CompiledAudioProgram) : AudioCommand

    data class StopMusic(val fadeFrames: Int) : AudioCommand {
        init {
            require(fadeFrames >= 0)
        }
    }

    data class PlaySfx(
        val program: CompiledAudioProgram,
        val name: SfxName,
    ) : AudioCommand

    data class SetControl(
        val name: AudioControlName,
        val value: Float,
    ) : AudioCommand {
        init {
            require(value.isFinite())
        }
    }

    data object Destroy : AudioCommand
}
