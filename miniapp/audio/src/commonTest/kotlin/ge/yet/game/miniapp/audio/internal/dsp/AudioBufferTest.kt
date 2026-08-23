package ge.yet.game.miniapp.audio.internal.dsp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudioBufferTest {
    @Test
    fun `stereo buffer is preallocated cleared and sanitizes non finite samples`() {
        val buffer = StereoAudioBuffer(8)
        buffer.left[0] = Float.NaN
        buffer.right[0] = Float.POSITIVE_INFINITY
        buffer.sanitize(1)

        assertEquals(0f, buffer.left[0])
        assertEquals(0f, buffer.right[0])

        buffer.left.fill(1f)
        buffer.right.fill(-1f)
        buffer.clear(4)
        assertTrue(buffer.left.take(4).all { it == 0f })
        assertTrue(buffer.right.take(4).all { it == 0f })
        assertEquals(1f, buffer.left[4])
    }
}
