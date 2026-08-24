package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnvelopeTest {
    @Test
    fun `adsr reaches exact phase boundaries and bounded release`() {
        val state = EnvelopeState(
            sampleRate = 1_000,
            attackSeconds = 0.010,
            decaySeconds = 0.020,
            sustain = 0.4f,
            releaseSeconds = 0.010,
        )
        state.noteOn()

        val attack = List(10) { state.nextValue() }
        val decay = List(20) { state.nextValue() }
        val sustain = state.nextValue()
        state.noteOff()
        val release = List(10) { state.nextValue() }

        assertClose(1f, attack.last())
        assertClose(0.4f, decay.last())
        assertClose(0.4f, sustain)
        assertClose(0f, release.last())
        assertEquals(EnvelopePhase.DONE, state.phase)
        assertTrue((attack + decay + release).all { it in 0f..1f })
    }

    @Test
    fun `zero duration phases transition without non finite samples`() {
        val state = EnvelopeState(48_000, 0.0, 0.0, 0.7f, 0.0)
        state.noteOn()
        assertClose(0.7f, state.nextValue())
        state.noteOff()
        assertClose(0f, state.nextValue())
    }

    @Test
    fun `envelope processes a caller owned block in place`() {
        val state = EnvelopeState(1_000, 0.004, 0.0, 1f, 0.0)
        val buffer = FloatArray(4) { 0.5f }
        state.noteOn()

        applyEnvelope(buffer, state, buffer.size)

        assertEquals(listOf(0.125f, 0.25f, 0.375f, 0.5f), buffer.toList())
    }
}

private fun assertClose(expected: Float, actual: Float) {
    assertTrue(abs(expected - actual) <= 0.0001f, "expected=$expected actual=$actual")
}
