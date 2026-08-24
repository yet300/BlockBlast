package ge.yet.game.miniapp.testkit

import com.arkivanov.decompose.ComponentContext
import ge.yet.game.miniapp.api.MiniAppSessionHost
import ge.yet.game.miniapp.api.MiniAppStorage
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.compose.MiniAppSessionContext

class TestMiniAppSessionContext(
    override val componentContext: ComponentContext,
    override val visibility: MiniAppVisibilitySource,
    override val host: MiniAppSessionHost,
    override val storage: MiniAppStorage = NoopMiniAppStorage,
    override val audio: MiniAppAudio = NoopMiniAppAudio,
) : MiniAppSessionContext

object NoopMiniAppAudio : MiniAppAudio {
    override fun playMusic(program: AudioProgram): AudioCommandResult = unavailable()
    override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult = unavailable()
    override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult = unavailable()
    override fun setControl(name: AudioControlName, value: Float): AudioCommandResult = unavailable()

    private fun unavailable(): AudioCommandResult =
        AudioCommandResult.Rejected(AudioCommandRejection.BACKEND_UNAVAILABLE)
}
