package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertTrue

class FilterTest {
    @Test
    fun `preallocated coefficients can be reconfigured in place`() {
        val coefficients = BiquadCoefficients.identity()

        coefficients.resetLowPass(sampleRate = 8_000, frequencyHz = 300.0, q = 0.707)

        assertTrue(coefficients.b0 > 0.0)
        assertTrue(coefficients.b1 > coefficients.b0)
    }

    @Test
    fun `low and high pass select opposite sides of a mixed signal`() {
        val sampleRate = 8_000
        val mixed = FloatArray(sampleRate) { index ->
            (0.5 * sin(2.0 * PI * 100.0 * index / sampleRate) +
                0.5 * sin(2.0 * PI * 2_000.0 * index / sampleRate)).toFloat()
        }
        val low = mixed.copyOf()
        val high = mixed.copyOf()
        processBiquad(low, BiquadCoefficients.lowPass(sampleRate, 300.0, 0.707), BiquadState())
        processBiquad(high, BiquadCoefficients.highPass(sampleRate, 1_000.0, 0.707), BiquadState())

        assertTrue(correlation(low, 100.0, sampleRate) > correlation(low, 2_000.0, sampleRate) * 4)
        assertTrue(correlation(high, 2_000.0, sampleRate) > correlation(high, 100.0, sampleRate) * 4)
    }

    @Test
    fun `band pass stays finite and favors its center frequency`() {
        val sampleRate = 8_000
        val impulse = FloatArray(4_096).also { it[0] = 1f }
        processBiquad(impulse, BiquadCoefficients.bandPass(sampleRate, 800.0, 2.0), BiquadState())

        assertTrue(impulse.all { it.isFinite() && abs(it) < 2f })
        assertTrue(abs(impulse.last()) < 0.0001f)
    }
}

private fun correlation(samples: FloatArray, frequency: Double, sampleRate: Int): Double =
    abs(samples.indices.sumOf { samples[it] * sin(2.0 * PI * frequency * it / sampleRate) })
