package ge.yet.game.miniapp.audio.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StereoPcmRingBufferTest {
    @Test
    fun `capacity must be a positive power of two`() {
        assertFailsWith<IllegalArgumentException> { StereoPcmRingBuffer(0) }
        assertFailsWith<IllegalArgumentException> { StereoPcmRingBuffer(6) }
        assertEquals(8, StereoPcmRingBuffer(8).capacityFrames)
    }

    @Test
    fun `write and read preserve stereo frame order`() {
        val ring = StereoPcmRingBuffer(8)
        val left = floatArrayOf(1f, 2f, 3f)
        val right = floatArrayOf(-1f, -2f, -3f)
        val outputLeft = FloatArray(3)
        val outputRight = FloatArray(3)

        assertEquals(3, ring.write(left, right, 3))
        assertEquals(0, ring.readOrSilence(outputLeft, outputRight, 3))
        assertContentEquals(left, outputLeft)
        assertContentEquals(right, outputRight)
        assertEquals(0, ring.availableFrames)
    }

    @Test
    fun `write and read preserve order across physical wrap`() {
        val ring = StereoPcmRingBuffer(8)
        val discardedLeft = FloatArray(4)
        val discardedRight = FloatArray(4)
        ring.write(
            left = floatArrayOf(1f, 2f, 3f, 4f, 5f, 6f),
            right = floatArrayOf(-1f, -2f, -3f, -4f, -5f, -6f),
            frameCount = 6,
        )
        ring.readOrSilence(discardedLeft, discardedRight, 4)

        assertEquals(
            6,
            ring.write(
                left = floatArrayOf(7f, 8f, 9f, 10f, 11f, 12f),
                right = floatArrayOf(-7f, -8f, -9f, -10f, -11f, -12f),
                frameCount = 6,
            ),
        )
        val outputLeft = FloatArray(8)
        val outputRight = FloatArray(8)

        assertEquals(0, ring.readOrSilence(outputLeft, outputRight, 8))
        assertContentEquals(floatArrayOf(5f, 6f, 7f, 8f, 9f, 10f, 11f, 12f), outputLeft)
        assertContentEquals(floatArrayOf(-5f, -6f, -7f, -8f, -9f, -10f, -11f, -12f), outputRight)
    }

    @Test
    fun `full ring refuses to overwrite unread frames`() {
        val ring = StereoPcmRingBuffer(8)
        val left = FloatArray(8) { (it + 1).toFloat() }
        val right = FloatArray(8) { -(it + 1).toFloat() }

        assertEquals(8, ring.write(left, right, 8))
        assertEquals(0, ring.freeFrames)
        assertEquals(0, ring.write(floatArrayOf(99f), floatArrayOf(-99f), 1))
        assertEquals(8, ring.availableFrames)

        val outputLeft = FloatArray(8)
        val outputRight = FloatArray(8)
        assertEquals(0, ring.readOrSilence(outputLeft, outputRight, 8))
        assertContentEquals(left, outputLeft)
        assertContentEquals(right, outputRight)
    }

    @Test
    fun `partial underrun fills the missing suffix with silence`() {
        val ring = StereoPcmRingBuffer(8)
        ring.write(floatArrayOf(1f, 2f), floatArrayOf(-1f, -2f), 2)
        val outputLeft = FloatArray(5) { 99f }
        val outputRight = FloatArray(5) { -99f }

        assertEquals(3, ring.readOrSilence(outputLeft, outputRight, 5))
        assertContentEquals(floatArrayOf(1f, 2f, 0f, 0f, 0f), outputLeft)
        assertContentEquals(floatArrayOf(-1f, -2f, 0f, 0f, 0f), outputRight)
        assertEquals(0, ring.availableFrames)
    }

    @Test
    fun `reset discards buffered frames and permits reuse`() {
        val ring = StereoPcmRingBuffer(8)
        ring.write(floatArrayOf(1f, 2f, 3f), floatArrayOf(-1f, -2f, -3f), 3)

        ring.reset()

        assertEquals(0, ring.availableFrames)
        assertEquals(8, ring.freeFrames)
        assertEquals(2, ring.write(floatArrayOf(7f, 8f), floatArrayOf(-7f, -8f), 2))
        val outputLeft = FloatArray(2)
        val outputRight = FloatArray(2)
        assertEquals(0, ring.readOrSilence(outputLeft, outputRight, 2))
        assertContentEquals(floatArrayOf(7f, 8f), outputLeft)
        assertContentEquals(floatArrayOf(-7f, -8f), outputRight)
    }
}
