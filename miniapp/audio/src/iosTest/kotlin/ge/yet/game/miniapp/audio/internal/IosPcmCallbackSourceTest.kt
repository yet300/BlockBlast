package ge.yet.game.miniapp.audio.internal

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class IosPcmCallbackSourceTest {
    @Test
    fun `render drains prepared pcm and diagnoses a partial underrun`() {
        val ring = StereoPcmRingBuffer(8)
        ring.write(
            left = floatArrayOf(1f, 2f, 3f, 4f, 5f),
            right = floatArrayOf(-1f, -2f, -3f, -4f, -5f),
            frameCount = 5,
        )
        val source = IosPcmCallbackSource(ring)
        val firstLeft = FloatArray(3)
        val firstRight = FloatArray(3)
        val secondLeft = FloatArray(5) { 99f }
        val secondRight = FloatArray(5) { -99f }

        source.render(firstLeft, firstRight, 3)
        source.render(secondLeft, secondRight, 5)

        assertContentEquals(floatArrayOf(1f, 2f, 3f), firstLeft)
        assertContentEquals(floatArrayOf(-1f, -2f, -3f), firstRight)
        assertContentEquals(floatArrayOf(4f, 5f, 0f, 0f, 0f), secondLeft)
        assertContentEquals(floatArrayOf(-4f, -5f, 0f, 0f, 0f), secondRight)
        assertEquals(
            IosPcmCallbackDiagnostics(
                renderedFrames = 5,
                underrunFrames = 3,
                underrunEvents = 1,
                callbackFailures = 0,
            ),
            source.drainDiagnostics(),
        )
    }

    @Test
    fun `diagnostics drain once and callback barrier closes on every valid exit`() {
        val source = IosPcmCallbackSource(StereoPcmRingBuffer(8))
        val left = FloatArray(2)
        val right = FloatArray(2)

        source.render(left, right, 0)
        source.render(left, right, 2)
        source.recordCallbackFailure()
        source.recordCallbackFailure()

        assertFalse(source.hasCallbackInFlight())
        assertEquals(
            IosPcmCallbackDiagnostics(
                renderedFrames = 0,
                underrunFrames = 2,
                underrunEvents = 1,
                callbackFailures = 2,
            ),
            source.drainDiagnostics(),
        )
        assertEquals(IosPcmCallbackDiagnostics(), source.drainDiagnostics())
    }
}
