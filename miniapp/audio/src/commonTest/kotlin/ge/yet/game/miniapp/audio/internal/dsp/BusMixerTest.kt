package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertTrue

class BusMixerTest {
    @Test
    fun `equal power pan preserves energy at center and reaches hard sides`() {
        val mono = FloatArray(8) { 1f }
        val left = FloatArray(8)
        val right = FloatArray(8)

        mixMonoToStereo(mono, left, right, frameCount = 8, gain = 1f, pan = 0f)

        assertClose(sqrt(0.5f), left[0])
        assertClose(sqrt(0.5f), right[0])
        assertClose(1f, left[0] * left[0] + right[0] * right[0])

        left.fill(0f)
        right.fill(0f)
        mixMonoToStereo(mono, left, right, frameCount = 8, gain = 1f, pan = -1f)
        assertClose(1f, left[0])
        assertClose(0f, right[0])
    }

    @Test
    fun `bus gain ramps smoothly and carries state across blocks`() {
        val state = SmoothedGainState(initial = 0f)
        val first = FloatArray(4) { 1f }
        val second = FloatArray(4) { 1f }

        applySmoothedGain(first, target = 1f, rampFrames = 8, state = state)
        applySmoothedGain(second, target = 1f, rampFrames = 8, state = state)

        assertTrue(first.asList().zipWithNext().all { (a, b) -> b > a })
        assertTrue(second.asList().zipWithNext().all { (a, b) -> b > a })
        assertClose(1f, second.last())
    }

    @Test
    fun `final limiter sanitizes and bounds stereo output`() {
        val left = floatArrayOf(Float.NaN, -4f, -0.5f, 0.5f, 4f)
        val right = floatArrayOf(Float.POSITIVE_INFINITY, 8f, -8f, 0f, 1f)

        limitStereo(left, right, frameCount = left.size, ceiling = 0.9f)

        assertTrue((left + right).all { it.isFinite() && abs(it) <= 0.9001f })
        assertClose(0f, left[0])
        assertClose(0f, right[0])
    }
}

private fun assertClose(expected: Float, actual: Float) {
    assertTrue(abs(expected - actual) < 0.0001f, "expected=$expected actual=$actual")
}
