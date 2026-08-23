package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class ModulatorTest {
    @Test
    fun `sine lfo is bounded and continuous across blocks`() {
        val state = LfoState()
        val split = FloatArray(2_000)
        renderSineLfo(2.0, 1_000, state, split, 1_000, 0)
        renderSineLfo(2.0, 1_000, state, split, 1_000, 1_000)
        val whole = FloatArray(2_000)
        renderSineLfo(2.0, 1_000, LfoState(), whole, whole.size, 0)

        assertContentEquals(whole, split)
        assertTrue(split.all { it in -1f..1f })
    }

    @Test
    fun `seeded smooth noise is deterministic and continuous`() {
        val first = FloatArray(4_000)
        val second = FloatArray(4_000)
        renderSmoothNoise(3.0, 1_000, SmoothNoiseState(99), first, first.size)
        renderSmoothNoise(3.0, 1_000, SmoothNoiseState(99), second, second.size)

        assertContentEquals(first, second)
        assertTrue((1 until first.size).maxOf { abs(first[it] - first[it - 1]) } < 0.02f)
    }
}
