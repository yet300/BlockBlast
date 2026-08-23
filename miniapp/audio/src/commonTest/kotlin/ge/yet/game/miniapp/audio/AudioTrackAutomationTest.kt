package ge.yet.game.miniapp.audio

import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderResult
import ge.yet.game.miniapp.audio.internal.OfflineAudioRenderer
import ge.yet.game.miniapp.audio.internal.OfflineAudioRequest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AudioTrackAutomationTest {
    @Test
    fun `track snapshots bounded gain and pan automation expressions`() {
        val program = audioProgram {
            control("intensity", default = 0.5f, range = 0f..1f)
            instrument("tone") { oscillator(OscillatorShape.SINE) }
            musicTrack("moving") {
                instrument("tone")
                notes(MidiNote.of(69))
                gain(control("intensity").map(0f, 0.8f) * sineLfo(0.5.hz, 0.5f..1f))
                pan(smoothNoise(seed = 42, rate = 0.75.hz, range = -0.8f..0.8f))
            }
        }

        val track = program.musicTracks.single()

        assertIs<AudioParameter.Product>(track.gain)
        assertIs<AudioParameter.SmoothNoise>(track.pan)
        assertIs<AudioCompilationResult.Success>(program.compile())
    }

    @Test
    fun `unknown controls and out of range track automation fail compilation`() {
        val program = audioProgram {
            instrument("tone") { oscillator(OscillatorShape.SINE) }
            musicTrack("invalid") {
                instrument("tone")
                notes(MidiNote.of(60))
                gain(control("missing").map(0f, 1f))
                pan(sineLfo(1.hz, -1.2f..1.2f))
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(
                AudioDiagnosticCode.UNRESOLVED_CONTROL to "musicTrack[invalid].gain",
                AudioDiagnosticCode.PARAMETER_RANGE_INVALID to "musicTrack[invalid].pan",
            ),
            failure.diagnostics.map { it.code to it.path },
        )
    }

    @Test
    fun `automated stereo render is deterministic and moves between channels`() {
        val program = audioProgram {
            instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.35f) }
            musicTrack("moving") {
                instrument("tone")
                notes(MidiNote.of(69))
                gain(sineLfo(0.5.hz, 0.35f..0.9f))
                pan(smoothNoise(seed = 99, rate = 1.hz, range = -0.9f..0.9f))
            }
        }
        val request = OfflineAudioRequest(sampleRate = 8_000, frameCount = 16_000)

        val first = assertIs<OfflineAudioRenderResult.Success>(OfflineAudioRenderer.render(program, request)).audio
        val second = assertIs<OfflineAudioRenderResult.Success>(OfflineAudioRenderer.render(program, request)).audio
        val channelDifference = List(4) { window ->
            val range = window * 4_000 until (window + 1) * 4_000
            range.sumOf { abs(first.left[it]).toDouble() - abs(first.right[it]).toDouble() }
        }

        assertEquals(first.quantizedPcmHash(), second.quantizedPcmHash())
        assertTrue(channelDifference.max() - channelDifference.min() > 20.0)
        assertTrue(first.peak <= 1f)
    }

    @Test
    fun `parameter expression depth is bounded before rendering`() {
        var parameter = audioParameter(1f)
        repeat(8) { parameter *= audioParameter(1f) }
        val program = audioProgram {
            instrument("tone") { oscillator(OscillatorShape.SINE) }
            musicTrack("deep") {
                instrument("tone")
                notes(MidiNote.of(60))
                gain(parameter)
            }
        }

        val failure = assertIs<AudioCompilationResult.Failure>(program.compile())

        assertEquals(
            listOf(AudioDiagnosticCode.PARAMETER_DEPTH_EXCEEDED to "musicTrack[deep].gain"),
            failure.diagnostics.map { it.code to it.path },
        )
    }
}
