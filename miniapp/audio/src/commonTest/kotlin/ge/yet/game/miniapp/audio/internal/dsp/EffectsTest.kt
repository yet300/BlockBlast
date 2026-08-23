package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EffectsTest {
    @Test
    fun `distortion is bounded monotonic and sanitizes non finite input`() {
        val buffer = floatArrayOf(Float.NEGATIVE_INFINITY, -1f, -0.25f, 0f, 0.25f, 1f, Float.NaN)

        applyDistortion(buffer, amount = 0.75f)

        assertTrue(buffer.all { it.isFinite() && it in -1f..1f })
        assertEquals(0f, buffer.first())
        assertEquals(0f, buffer.last())
        assertTrue(buffer.slice(1..5).zipWithNext().all { (left, right) -> left <= right })
    }

    @Test
    fun `bit crush quantizes amplitude and holds reduced samples across blocks`() {
        val state = BitCrusherState()
        val first = floatArrayOf(-0.9f, -0.6f, -0.2f, 0.2f, 0.6f)
        val second = floatArrayOf(0.9f, 0.4f, -0.4f)

        applyBitCrush(first, bitDepth = 3, sampleRateReduction = 2, state = state)
        applyBitCrush(second, bitDepth = 3, sampleRateReduction = 2, state = state)

        assertEquals(first[0], first[1])
        assertEquals(first[2], first[3])
        assertEquals(first[4], second[0])
        assertTrue((first + second).all { value -> abs(value * 3f - (value * 3f).toInt()) < 0.0001f })
    }

    @Test
    fun `delay uses preallocated storage and produces a bounded feedback tail`() {
        val state = DelayState(maxDelayFrames = 16)
        val buffer = FloatArray(12).also { it[0] = 1f }

        applyDelay(buffer, delayFrames = 3, feedback = 0.5f, wet = 1f, state = state)

        assertClose(1f, buffer[0])
        assertClose(1f, buffer[3])
        assertClose(0.5f, buffer[6])
        assertClose(0.25f, buffer[9])
    }

    @Test
    fun `algorithmic reverb is deterministic bounded and has a decaying tail`() {
        fun render(): FloatArray {
            val buffer = FloatArray(8_000).also { it[0] = 1f }
            applyReverb(buffer, send = 0.8f, state = ReverbState(sampleRate = 8_000))
            return buffer
        }

        val first = render()
        val second = render()

        assertTrue(first.contentEquals(second))
        assertTrue(first.all { it.isFinite() && abs(it) <= 2f })
        assertTrue(first.drop(100).any { abs(it) > 0.0001f })
        assertTrue(rms(first, 0, 2_000) > rms(first, 6_000, 8_000))
    }
}

private fun assertClose(expected: Float, actual: Float) {
    assertTrue(abs(expected - actual) < 0.0001f, "expected=$expected actual=$actual")
}

private fun rms(values: FloatArray, start: Int, end: Int): Double {
    var sum = 0.0
    for (index in start until end) sum += values[index] * values[index]
    return kotlin.math.sqrt(sum / (end - start))
}
