package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.hz
import ge.yet.game.miniapp.audio.ms
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class VoiceTest {
    @Test
    fun `fixed voice pipeline renders sources envelope filter and effects in order`() {
        val instrument = audioProgram {
            instrument("lead") {
                oscillator(OscillatorShape.SAW, gain = 0.7f)
                partial(ratio = 2f, gain = 0.15f)
                envelope(attack = 4.ms, decay = 8.ms, sustain = 0.6f, release = 4.ms)
                lowPass(2_000.hz)
                distortion(0.1f)
                bitCrush(bitDepth = 12, sampleRateReduction = 2)
            }
        }.instruments.single()
        val state = VoiceState(instrument, MidiNote.of(69), sampleRate = 8_000, blockCapacity = 512)
        val first = FloatArray(512)
        val second = FloatArray(512)

        state.render(first, first.size)
        state.render(second, second.size)

        assertTrue((first + second).all { it.isFinite() && abs(it) <= 1.1f })
        assertTrue(first.take(16).zipWithNext().any { (a, b) -> abs(b) > abs(a) })
        assertTrue(first.any { abs(it) > 0.05f })
        assertTrue(!first.contentEquals(second), "voice state must continue across blocks")
    }

    @Test
    fun `frequency modulation and vibrato alter the carrier deterministically`() {
        fun render(modulated: Boolean): FloatArray {
            val instrument = audioProgram {
                instrument("lead") {
                    oscillator(OscillatorShape.SINE)
                    if (modulated) {
                        frequencyModulation(ratio = 2f, index = 1f)
                        vibrato(rate = 5.hz, depthCents = 12f)
                    }
                }
            }.instruments.single()
            return FloatArray(1_000).also {
                VoiceState(instrument, MidiNote.of(69), 8_000, it.size).render(it, it.size)
            }
        }

        val plain = render(false)
        val first = render(true)
        val second = render(true)

        assertTrue(first.contentEquals(second))
        assertTrue(!first.contentEquals(plain))
    }
}
