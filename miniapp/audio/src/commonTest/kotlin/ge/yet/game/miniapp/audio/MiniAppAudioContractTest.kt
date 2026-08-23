package ge.yet.game.miniapp.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MiniAppAudioContractTest {
    @Test
    fun `gameplay commands expose typed results without backend exceptions`() {
        val audio = RecordingMiniAppAudio()
        val program = audioProgram {
            instrument("lead") { oscillator(OscillatorShape.SINE) }
            musicTrack("line") {
                instrument("lead")
                notes(MidiNote.of(60))
            }
            sfx("place") { oscillator(OscillatorShape.SQUARE) }
            control("intensity", default = 0.5f, range = 0f..1f)
        }

        assertIs<AudioCommandResult.Accepted>(audio.playMusic(program))
        assertIs<AudioCommandResult.Accepted>(audio.playSfx(program, SfxName("place")))
        assertIs<AudioCommandResult.Accepted>(audio.setControl(AudioControlName("intensity"), 0.8f))
        assertIs<AudioCommandResult.Accepted>(audio.stopMusic())
        assertEquals(AudioDuration.DefaultFade, audio.lastFadeOut)
    }

    @Test
    fun `rejected command snapshots structured diagnostics`() {
        val source = mutableListOf(
            AudioDiagnostic(
                code = AudioDiagnosticCode.UNRESOLVED_INSTRUMENT,
                path = "musicTrack[line].instrument[missing]",
                message = "Unknown instrument 'missing'",
            ),
        )
        val result = AudioCommandResult.Rejected(
            reason = AudioCommandRejection.INVALID_PROGRAM,
            diagnostics = source,
        )

        source.clear()

        assertEquals(1, result.diagnostics.size)
        assertEquals(AudioCommandRejection.INVALID_PROGRAM, result.reason)
    }

    private class RecordingMiniAppAudio : MiniAppAudio {
        var lastFadeOut: AudioDuration? = null

        override fun playMusic(program: AudioProgram): AudioCommandResult = AudioCommandResult.Accepted

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult {
            lastFadeOut = fadeOut
            return AudioCommandResult.Accepted
        }

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult = AudioCommandResult.Accepted

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult = AudioCommandResult.Accepted
    }
}
