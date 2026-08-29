package ge.yet.game.miniapp.audio.testing

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalMiniAppAudioTestingApi::class)
class MiniAppAudioTestRendererTest {
    @Test
    fun `legacy music overload preserves offline pcm and metrics`() {
        val pcm = renderSuccess(
            MiniAppAudioTestRenderer.render(
                legacyMusicProgram(),
                sampleRate = SAMPLE_RATE,
                frameCount = FRAME_COUNT,
            ),
        )

        assertEquals(SAMPLE_RATE, pcm.sampleRate)
        assertEquals(FRAME_COUNT, pcm.frameCount)
        assertEquals(6_138_225_666_948_195_473L, pcm.quantizedPcmHash)
        assertEquals(0.35700378f, pcm.peak, absoluteTolerance = 0.000001f)
        assertEquals(0.16977849607383683, pcm.rms, absoluteTolerance = 0.000000001)
        assertTrue(pcm.left.all(Float::isFinite))
        assertTrue(pcm.right.all(Float::isFinite))
    }

    @Test
    fun `legacy music overload preserves invalid argument exceptions`() {
        val program = legacyMusicProgram()

        assertFailsWith<IllegalArgumentException> {
            MiniAppAudioTestRenderer.render(program, sampleRate = 7_999, frameCount = FRAME_COUNT)
        }
        assertFailsWith<IllegalArgumentException> {
            MiniAppAudioTestRenderer.render(program, sampleRate = SAMPLE_RATE, frameCount = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            MiniAppAudioTestRenderer.render(
                program,
                sampleRate = SAMPLE_RATE,
                frameCount = SAMPLE_RATE * 60 + 1,
            )
        }
    }

    @Test
    fun `sfx-only rendering is deterministic and mutes music`() {
        val program = controlledProgram()
        val request = AudioTestRenderRequest(
            sampleRate = SAMPLE_RATE,
            frameCount = FRAME_COUNT,
            includeMusic = false,
            sfxTriggers = listOf(AudioTestSfxTrigger(SfxName("click"), frameOffset = 128)),
        )

        val first = renderSuccess(MiniAppAudioTestRenderer.render(program, request))
        val second = renderSuccess(MiniAppAudioTestRenderer.render(program, request))

        assertEquals(first.quantizedPcmHash, second.quantizedPcmHash)
        assertTrue(first.left.take(128).all { it == 0f })
        assertTrue(first.left.drop(128).any { it != 0f })
    }

    @Test
    fun `control extremes change pcm deterministically before first block`() {
        val program = controlledProgram()
        fun render(value: Float) = renderSuccess(
            MiniAppAudioTestRenderer.render(
                program,
                AudioTestRenderRequest(
                    sampleRate = SAMPLE_RATE,
                    frameCount = FRAME_COUNT,
                    controls = listOf(AudioTestControlValue(AudioControlName("intensity"), value)),
                ),
            ),
        )

        val quiet = render(0f)
        val loud = render(1f)
        val loudAgain = render(1f)

        assertNotEquals(quiet.quantizedPcmHash, loud.quantizedPcmHash)
        assertEquals(loud.quantizedPcmHash, loudAgain.quantizedPcmHash)
        assertTrue(quiet.rms < loud.rms)
    }

    @Test
    fun `multiple triggers begin at their requested frame offsets`() {
        val program = controlledProgram()
        val firstOnly = renderSuccess(
            MiniAppAudioTestRenderer.render(
                program,
                AudioTestRenderRequest(
                    sampleRate = SAMPLE_RATE,
                    frameCount = FRAME_COUNT,
                    includeMusic = false,
                    sfxTriggers = listOf(AudioTestSfxTrigger(SfxName("click"), 64)),
                ),
            ),
        )
        val mixed = renderSuccess(
            MiniAppAudioTestRenderer.render(
                program,
                AudioTestRenderRequest(
                    sampleRate = SAMPLE_RATE,
                    frameCount = FRAME_COUNT,
                    includeMusic = false,
                    sfxTriggers = listOf(
                        AudioTestSfxTrigger(SfxName("click"), 64),
                        AudioTestSfxTrigger(SfxName("chime"), 320),
                    ),
                ),
            ),
        )

        assertContentEquals(firstOnly.left.copyOfRange(0, 320), mixed.left.copyOfRange(0, 320))
        assertTrue(
            firstOnly.left.indices.drop(320).any { firstOnly.left[it] != mixed.left[it] },
            "The second trigger must enter the mix exactly at its requested offset",
        )
    }

    @Test
    fun `rapid same-frame triggers are deterministic through voice shedding and limiter`() {
        val program = controlledProgram()
        val ordered = List(48) { index ->
            AudioTestSfxTrigger(SfxName(if (index < 24) "click" else "chime"), frameOffset = 0)
        }
        val reversed = ordered.reversed()
        fun render(triggers: List<AudioTestSfxTrigger>) = renderSuccess(
            MiniAppAudioTestRenderer.render(
                program,
                AudioTestRenderRequest(
                    sampleRate = SAMPLE_RATE,
                    frameCount = FRAME_COUNT,
                    includeMusic = false,
                    sfxTriggers = triggers,
                ),
            ),
        )

        val first = render(ordered)
        val repeated = render(ordered)
        val differentOrder = render(reversed)

        assertEquals(first.quantizedPcmHash, repeated.quantizedPcmHash)
        assertNotEquals(first.quantizedPcmHash, differentOrder.quantizedPcmHash)
        assertTrue(first.peak <= 1f)
        assertTrue(first.left.any { it != 0f })
    }

    @Test
    fun `invalid request content returns explicit errors`() {
        val program = controlledProgram()

        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                controls = listOf(AudioTestControlValue(AudioControlName("missing"), 0.5f)),
            ),
            AudioTestRequestErrorCode.UNKNOWN_CONTROL,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                sfxTriggers = listOf(AudioTestSfxTrigger(SfxName("missing"), 0)),
            ),
            AudioTestRequestErrorCode.UNKNOWN_SFX,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                controls = listOf(
                    AudioTestControlValue(AudioControlName("intensity"), 0f),
                    AudioTestControlValue(AudioControlName("intensity"), 1f),
                ),
            ),
            AudioTestRequestErrorCode.DUPLICATE_CONTROL,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                controls = listOf(AudioTestControlValue(AudioControlName("intensity"), Float.NaN)),
            ),
            AudioTestRequestErrorCode.NON_FINITE_CONTROL_VALUE,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                controls = listOf(AudioTestControlValue(AudioControlName("intensity"), 2f)),
            ),
            AudioTestRequestErrorCode.CONTROL_VALUE_OUT_OF_RANGE,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                sfxTriggers = listOf(AudioTestSfxTrigger(SfxName("click"), -1)),
            ),
            AudioTestRequestErrorCode.FRAME_OFFSET_OUT_OF_RANGE,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                sfxTriggers = listOf(AudioTestSfxTrigger(SfxName("click"), FRAME_COUNT)),
            ),
            AudioTestRequestErrorCode.FRAME_OFFSET_OUT_OF_RANGE,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                sfxTriggers = List(10_000) { AudioTestSfxTrigger(SfxName("click"), 0) },
            ),
            AudioTestRequestErrorCode.SFX_TRIGGER_LIMIT_EXCEEDED,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(
                SAMPLE_RATE,
                FRAME_COUNT,
                controls = List(33) { AudioTestControlValue(AudioControlName("intensity"), 0.5f) },
            ),
            AudioTestRequestErrorCode.CONTROL_OVERRIDE_LIMIT_EXCEEDED,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(sampleRate = 7_999, frameCount = FRAME_COUNT),
            AudioTestRequestErrorCode.SAMPLE_RATE_OUT_OF_RANGE,
        )
        assertRequestError(
            program,
            AudioTestRenderRequest(sampleRate = SAMPLE_RATE, frameCount = SAMPLE_RATE * 60 + 1),
            AudioTestRequestErrorCode.FRAME_COUNT_OUT_OF_RANGE,
        )
    }

    @Test
    fun `returned channel arrays remain defensive copies`() {
        val pcm = renderSuccess(
            MiniAppAudioTestRenderer.render(controlledProgram(), SAMPLE_RATE, FRAME_COUNT),
        )
        val expectedLeft = pcm.left
        val expectedRight = pcm.right

        pcm.left.fill(0f)
        pcm.right.fill(0f)

        assertContentEquals(expectedLeft, pcm.left)
        assertContentEquals(expectedRight, pcm.right)
    }

    @Test
    fun `invalid programs return public diagnostics without pcm`() {
        val invalid = audioProgram { instrument("silent") {} }

        val result = assertIs<AudioTestRenderResult.Failure>(
            MiniAppAudioTestRenderer.render(invalid, sampleRate = SAMPLE_RATE, frameCount = 100),
        )

        assertTrue(result.diagnostics.isNotEmpty())
        assertTrue(result.requestErrors.isEmpty())
    }

    private fun assertRequestError(
        program: AudioProgram,
        request: AudioTestRenderRequest,
        expected: AudioTestRequestErrorCode,
    ) {
        val result = assertIs<AudioTestRenderResult.Failure>(MiniAppAudioTestRenderer.render(program, request))
        assertTrue(result.requestErrors.any { it.code == expected })
    }

    private fun controlledProgram() = audioProgram {
        control("intensity", default = 0.5f, range = 0f..1f)
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.2f) }
        musicTrack("tone") {
            instrument("tone")
            notes(MidiNote.of(69))
            gain(control("intensity").map(0f, 0.8f))
        }
        sfx("click") {
            oscillator(OscillatorShape.SQUARE, gain = 0.3f)
            pitch(440.hz, 220.hz, 60.ms)
            envelope(0.ms, release = 30.ms)
        }
        sfx("chime") {
            oscillator(OscillatorShape.SINE, gain = 0.25f)
            pitch(880.hz, 660.hz, 80.ms)
            envelope(0.ms, release = 40.ms)
        }
    }

    private fun legacyMusicProgram() = audioProgram {
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.25f) }
        musicTrack("tone") {
            instrument("tone")
            notes(MidiNote.of(69), MidiNote.of(72))
            delay(time = 25.ms, feedback = 0.2f)
        }
        musicBus {
            delay(time = 40.ms, feedback = 0.15f)
            reverb(send = 0.08f)
        }
    }

    private fun renderSuccess(result: AudioTestRenderResult): AudioTestPcm =
        assertIs<AudioTestRenderResult.Success>(result).pcm

    private companion object {
        const val SAMPLE_RATE = 8_000
        const val FRAME_COUNT = 1_024
    }
}
