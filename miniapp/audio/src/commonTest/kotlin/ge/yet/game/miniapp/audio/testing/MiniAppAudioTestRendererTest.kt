package ge.yet.game.miniapp.audio.testing

import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgram
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
class MiniAppAudioTestRendererTest {
    @Test
    fun `public testing facade exposes deterministic defensive pcm metrics`() {
        val program = audioProgram {
            instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.25f) }
            musicTrack("tone") {
                instrument("tone")
                notes(MidiNote.of(69))
            }
        }

        val first = assertIs<AudioTestRenderResult.Success>(
            MiniAppAudioTestRenderer.render(program, sampleRate = 8_000, frameCount = 1_000),
        ).pcm
        val second = assertIs<AudioTestRenderResult.Success>(
            MiniAppAudioTestRenderer.render(program, sampleRate = 8_000, frameCount = 1_000),
        ).pcm
        val exposed = first.left
        exposed.fill(0f)

        assertEquals(first.quantizedPcmHash, second.quantizedPcmHash)
        assertTrue(first.left.any { it != 0f })
        assertTrue(first.peak in 0f..1f)
    }

    @Test
    fun `invalid programs return public diagnostics without pcm`() {
        val invalid = audioProgram { instrument("silent") {} }

        val result = assertIs<AudioTestRenderResult.Failure>(
            MiniAppAudioTestRenderer.render(invalid, sampleRate = 8_000, frameCount = 100),
        )

        assertTrue(result.diagnostics.isNotEmpty())
    }
}
