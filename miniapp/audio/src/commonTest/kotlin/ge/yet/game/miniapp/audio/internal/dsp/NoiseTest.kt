package ge.yet.game.miniapp.audio.internal.dsp

import ge.yet.game.miniapp.audio.NoiseColor
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class NoiseTest {
    @Test
    fun `seeded noise is deterministic finite and bounded`() {
        NoiseColor.entries.forEach { color ->
            val first = FloatArray(4_096)
            val second = FloatArray(4_096)
            renderNoise(color, NoiseState(42), first, first.size)
            renderNoise(color, NoiseState(42), second, second.size)

            assertContentEquals(first, second, color.name)
            assertTrue(first.all { it.isFinite() && abs(it) <= 1f }, color.name)
        }
    }

    @Test
    fun `pink and brown noise progressively reduce sample to sample roughness`() {
        fun roughness(color: NoiseColor): Double {
            val output = FloatArray(16_384)
            renderNoise(color, NoiseState(7), output, output.size)
            return (1 until output.size).sumOf { abs(output[it] - output[it - 1]).toDouble() } / (output.size - 1)
        }

        val white = roughness(NoiseColor.WHITE)
        val pink = roughness(NoiseColor.PINK)
        val brown = roughness(NoiseColor.BROWN)

        assertTrue(white > pink, "white=$white pink=$pink")
        assertTrue(pink > brown, "pink=$pink brown=$brown")
    }
}
