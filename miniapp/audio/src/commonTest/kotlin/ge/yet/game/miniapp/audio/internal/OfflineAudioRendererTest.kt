package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioNote
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.ms
import ge.yet.game.pattern.sequence
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OfflineAudioRendererTest {
    @Test
    fun `tiny sine program has stable frames metrics frequency and quantized hash`() {
        val program = audioProgram {
            tempo(240f)
            instrument("sine") {
                oscillator(OscillatorShape.SINE, gain = 0.5f)
                envelope(attack = 1.ms, sustain = 1f, release = 1.ms)
            }
            musicTrack("tone") {
                instrument("sine")
                notes(MidiNote.of(69))
            }
        }
        val request = OfflineAudioRequest(sampleRate = 8_000, frameCount = 8_000)

        val first = assertIs<OfflineAudioRenderResult.Success>(OfflineAudioRenderer.render(program, request))
        val second = assertIs<OfflineAudioRenderResult.Success>(OfflineAudioRenderer.render(program, request))

        assertEquals(8_000, first.audio.frameCount)
        assertTrue(first.audio.peak in 0.3f..0.6f)
        assertTrue(first.audio.rms in 0.2..0.5)
        assertTrue(abs(dominantCorrelationFrequency(first.audio.left, request.sampleRate) - 440) <= 2)
        assertEquals(first.audio.quantizedPcmHash(), second.audio.quantizedPcmHash())
        assertEquals(-2392149775764585971L, first.audio.quantizedPcmHash())
    }

    @Test
    fun `rest events remain silent while pitched events render`() {
        val pattern = sequence<AudioNote>(listOf(AudioNote.Rest, AudioNote.Pitched(MidiNote.of(60))))
        val program = audioProgram {
            tempo(240f)
            instrument("lead") { oscillator(OscillatorShape.SQUARE, gain = 0.25f) }
            musicTrack("line") {
                instrument("lead")
                notes(pattern)
            }
        }

        val success = assertIs<OfflineAudioRenderResult.Success>(
            OfflineAudioRenderer.render(program, OfflineAudioRequest(8_000, 8_000)),
        )

        assertTrue(success.audio.left.take(4_000).all { it == 0f })
        assertTrue(success.audio.left.drop(4_000).any { abs(it) > 0.01f })
    }

    @Test
    fun `invalid program returns diagnostics and no partial audio`() {
        val invalid = audioProgram {
            instrument("silent") {}
        }

        val failure = assertIs<OfflineAudioRenderResult.Failure>(
            OfflineAudioRenderer.render(invalid, OfflineAudioRequest(8_000, 100)),
        )

        assertTrue(failure.diagnostics.isNotEmpty())
    }

    @Test
    fun `mapped parameters use the declared control default`() {
        fun render(default: Float): OfflineAudioResult {
            val program = audioProgram {
                control("cutoff", default = default, range = 0f..1f)
                instrument("lead") {
                    oscillator(OscillatorShape.SAW, gain = 0.25f)
                    lowPass(control("cutoff").map(100f, 3_000f))
                }
                musicTrack("tone") {
                    instrument("lead")
                    notes(MidiNote.of(69))
                }
            }
            return assertIs<OfflineAudioRenderResult.Success>(
                OfflineAudioRenderer.render(program, OfflineAudioRequest(8_000, 2_000)),
            ).audio
        }

        val closed = render(default = 0f)
        val open = render(default = 1f)

        assertTrue(closed.quantizedPcmHash() != open.quantizedPcmHash())
        assertTrue(closed.rms < open.rms)
    }
}

private fun dominantCorrelationFrequency(samples: FloatArray, sampleRate: Int): Int {
    var bestFrequency = 0
    var best = Double.NEGATIVE_INFINITY
    for (frequency in 430..450) {
        var correlation = 0.0
        for (index in samples.indices) {
            correlation += samples[index] * sin(2.0 * PI * frequency * index / sampleRate)
        }
        if (abs(correlation) > best) {
            best = abs(correlation)
            bestFrequency = frequency
        }
    }
    return bestFrequency
}
