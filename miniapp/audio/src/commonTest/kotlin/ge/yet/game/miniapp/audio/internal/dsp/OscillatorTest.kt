package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.OscillatorShape
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class OscillatorTest {
    @Test
    fun `all oscillators keep amplitude bounded and preserve dominant frequency`() {
        val sampleRate = 8_000
        val frequency = 200.0

        OscillatorShape.entries.forEach { shape ->
            val output = FloatArray(sampleRate)
            renderOscillator(
                shape = shape,
                frequencyHz = frequency,
                pulseWidth = 0.3,
                sampleRate = sampleRate,
                state = OscillatorState(),
                output = output,
                frameCount = output.size,
            )

            assertTrue(output.all { it.isFinite() && abs(it) <= 1.0001f }, "$shape amplitude")
            assertTrue(abs(positiveCrossingFrequency(output, sampleRate) - frequency) <= 1.0, "$shape frequency")
        }
    }

    @Test
    fun `phase continues across caller owned output blocks`() {
        val state = OscillatorState()
        val split = FloatArray(800)
        renderOscillator(OscillatorShape.SINE, 100.0, 0.5, 8_000, state, split, 400, outputOffset = 0)
        renderOscillator(OscillatorShape.SINE, 100.0, 0.5, 8_000, state, split, 400, outputOffset = 400)
        val whole = FloatArray(800)
        renderOscillator(OscillatorShape.SINE, 100.0, 0.5, 8_000, OscillatorState(), whole, 800)

        assertTrue(split.contentEquals(whole))
    }
}

private fun positiveCrossingFrequency(samples: FloatArray, sampleRate: Int): Double {
    var crossings = 0
    for (index in 1 until samples.size) {
        if (samples[index - 1] <= 0f && samples[index] > 0f) crossings += 1
    }
    return crossings.toDouble() * sampleRate / samples.size
}
