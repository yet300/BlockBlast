package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AudioCommandQueueTest {
    @Test
    fun `regular commands preserve bounded fifo order`() {
        val queue = AudioCommandQueue(capacity = 3)
        val first = control("first", 0.1f)
        val second = control("second", 0.2f)
        val third = control("third", 0.3f)

        assertEquals(AudioCommandOfferResult.Accepted, queue.offer(first))
        assertEquals(AudioCommandOfferResult.Accepted, queue.offer(second))
        assertEquals(AudioCommandOfferResult.Accepted, queue.offer(third))
        assertEquals(AudioCommandOfferResult.RejectedFull, queue.offer(control("fourth", 0.4f)))

        assertEquals(first, queue.poll())
        assertEquals(second, queue.poll())
        assertEquals(third, queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `intermediate control updates coalesce without crossing a command barrier`() {
        val queue = AudioCommandQueue(capacity = 4)
        queue.offer(control("intensity", 0.1f))
        queue.offer(control("intensity", 0.8f))
        queue.offer(AudioCommand.StopMusic(fadeFrames = 64))
        queue.offer(control("intensity", 0.4f))

        assertEquals(3, queue.size)
        assertEquals(AudioCommandOfferResult.Coalesced, queue.offer(control("intensity", 0.6f)))
        assertEquals(3, queue.size)

        assertEquals(0.8f, assertIs<AudioCommand.SetControl>(queue.poll()).value)
        assertIs<AudioCommand.StopMusic>(queue.poll())
        assertEquals(0.6f, assertIs<AudioCommand.SetControl>(queue.poll()).value)
    }

    @Test
    fun `stop and destroy evict droppable commands instead of being rejected`() {
        val queue = AudioCommandQueue(capacity = 3)
        queue.offer(control("first", 0.1f))
        queue.offer(control("second", 0.2f))
        queue.offer(control("third", 0.3f))

        assertEquals(AudioCommandOfferResult.AcceptedAfterEviction, queue.offer(AudioCommand.StopMusic(32)))
        assertEquals(AudioCommandOfferResult.AcceptedAfterEviction, queue.offer(AudioCommand.Destroy))

        assertEquals("third", assertIs<AudioCommand.SetControl>(queue.poll()).name.value)
        assertIs<AudioCommand.StopMusic>(queue.poll())
        assertEquals(AudioCommand.Destroy, queue.poll())
    }

    @Test
    fun `duplicate critical commands coalesce when queue contains only critical work`() {
        val queue = AudioCommandQueue(capacity = 2)

        assertEquals(AudioCommandOfferResult.Accepted, queue.offer(AudioCommand.StopMusic(64)))
        assertEquals(AudioCommandOfferResult.Accepted, queue.offer(AudioCommand.Destroy))
        assertEquals(AudioCommandOfferResult.Coalesced, queue.offer(AudioCommand.StopMusic(16)))
        assertEquals(AudioCommandOfferResult.Coalesced, queue.offer(AudioCommand.Destroy))

        assertEquals(16, assertIs<AudioCommand.StopMusic>(queue.poll()).fadeFrames)
        assertEquals(AudioCommand.Destroy, queue.poll())
        assertNull(queue.poll())
    }

    @Test
    fun `eviction preserves fifo order after the ring wraps`() {
        val queue = AudioCommandQueue(capacity = 3)
        queue.offer(control("first", 0.1f))
        queue.offer(control("second", 0.2f))
        queue.offer(control("third", 0.3f))
        queue.poll()
        queue.offer(control("fourth", 0.4f))

        assertEquals(AudioCommandOfferResult.AcceptedAfterEviction, queue.offer(AudioCommand.StopMusic(8)))

        assertEquals("third", assertIs<AudioCommand.SetControl>(queue.poll()).name.value)
        assertEquals("fourth", assertIs<AudioCommand.SetControl>(queue.poll()).name.value)
        assertIs<AudioCommand.StopMusic>(queue.poll())
    }

    private fun control(name: String, value: Float) =
        AudioCommand.SetControl(AudioControlName(name), value)
}
