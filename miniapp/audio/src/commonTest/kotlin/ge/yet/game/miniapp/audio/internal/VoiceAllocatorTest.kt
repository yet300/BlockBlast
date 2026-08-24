package ge.yet.game.miniapp.audio.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VoiceAllocatorTest {
    @Test
    fun `realtime allocation exposes primitive result without a snapshot object`() {
        val allocator = VoiceAllocator(capacity = 2, sfxReserve = 1)

        assertTrue(allocator.allocateRealtime(VoiceKind.MUSIC, startedAtFrame = 0))
        val firstVoiceId = allocator.lastAllocatedVoiceId
        assertEquals(0L, allocator.lastStolenVoiceId)
        assertTrue(allocator.allocateRealtime(VoiceKind.SFX, startedAtFrame = 1))
        assertEquals(0L, allocator.lastStolenVoiceId)

        assertTrue(allocator.allocateRealtime(VoiceKind.MUSIC, startedAtFrame = 2))
        assertEquals(firstVoiceId, allocator.lastStolenVoiceId)
    }

    @Test
    fun `default mobile pool keeps eight of thirty two slots available for sfx`() {
        val allocator = VoiceAllocator()
        val firstMusic = allocator.allocated(VoiceKind.MUSIC, 0)
        repeat(23) { allocator.allocated(VoiceKind.MUSIC, (it + 1).toLong()) }

        val replacement = allocator.allocated(VoiceKind.MUSIC, 24)
        repeat(8) { allocator.allocated(VoiceKind.SFX, (25 + it).toLong()) }

        assertEquals(firstMusic.voiceId, replacement.stolenVoiceId)
        assertEquals(32, allocator.voiceCount)
        assertEquals(24, allocator.musicVoiceCount)
    }

    @Test
    fun `music steals music instead of consuming the sfx reserve`() {
        val allocator = VoiceAllocator(capacity = 4, sfxReserve = 1)
        val music = List(3) { index -> allocator.allocated(VoiceKind.MUSIC, index.toLong()) }

        val replacement = allocator.allocated(VoiceKind.MUSIC, startedAtFrame = 10)

        assertEquals(music.first().voiceId, replacement.stolenVoiceId)
        assertEquals(3, allocator.voiceCount)
        assertEquals(3, allocator.musicVoiceCount)
        assertNull(allocator.allocated(VoiceKind.SFX, 11).stolenVoiceId)
        assertEquals(4, allocator.voiceCount)
    }

    @Test
    fun `sfx steals a released voice before quieter active voices`() {
        val allocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val released = allocator.allocated(VoiceKind.MUSIC, 0)
        val quiet = allocator.allocated(VoiceKind.SFX, 1)
        allocator.updateLevel(released.voiceId, 1f)
        allocator.updateLevel(quiet.voiceId, 0f)
        allocator.markReleased(released.voiceId)

        val replacement = allocator.allocated(VoiceKind.SFX, 2)

        assertEquals(released.voiceId, replacement.stolenVoiceId)
    }

    @Test
    fun `quietest then oldest voice wins active victim selection`() {
        val quietestAllocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val loudOld = quietestAllocator.allocated(VoiceKind.MUSIC, 0)
        val quietNew = quietestAllocator.allocated(VoiceKind.SFX, 10)
        quietestAllocator.updateLevel(loudOld.voiceId, 0.8f)
        quietestAllocator.updateLevel(quietNew.voiceId, 0.2f)

        assertEquals(
            quietNew.voiceId,
            quietestAllocator.allocated(VoiceKind.SFX, 20).stolenVoiceId,
        )

        val oldestAllocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val oldest = oldestAllocator.allocated(VoiceKind.MUSIC, 0)
        val newest = oldestAllocator.allocated(VoiceKind.SFX, 10)
        oldestAllocator.updateLevel(oldest.voiceId, 0.5f)
        oldestAllocator.updateLevel(newest.voiceId, 0.5f)

        assertEquals(
            oldest.voiceId,
            oldestAllocator.allocated(VoiceKind.SFX, 20).stolenVoiceId,
        )
    }

    @Test
    fun `music priority and stable id break otherwise equal ties`() {
        val kindAllocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val sfx = kindAllocator.allocated(VoiceKind.SFX, 0)
        val music = kindAllocator.allocated(VoiceKind.MUSIC, 0)

        assertEquals(music.voiceId, kindAllocator.allocated(VoiceKind.SFX, 1).stolenVoiceId)

        val idAllocator = VoiceAllocator(capacity = 3, sfxReserve = 1)
        val first = idAllocator.allocated(VoiceKind.SFX, 0)
        idAllocator.allocated(VoiceKind.SFX, 0)
        idAllocator.allocated(VoiceKind.SFX, 0)

        assertEquals(first.voiceId, idAllocator.allocated(VoiceKind.SFX, 1).stolenVoiceId)
        assertTrue(sfx.voiceId < music.voiceId)
    }

    @Test
    fun `music never steals an sfx voice when no music candidate exists`() {
        val allocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        allocator.allocated(VoiceKind.SFX, 0)
        allocator.allocated(VoiceKind.SFX, 1)

        assertIs<VoiceAllocationResult.Rejected>(allocator.allocate(VoiceKind.MUSIC, 2))
        assertEquals(2, allocator.voiceCount)
    }

    @Test
    fun `finish releases a slot and stale voice ids cannot mutate its replacement`() {
        val allocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val old = allocator.allocated(VoiceKind.SFX, 0)

        assertTrue(allocator.finish(old.voiceId))
        val replacement = allocator.allocated(VoiceKind.SFX, 1)

        assertFalse(allocator.updateLevel(old.voiceId, 0.2f))
        assertFalse(allocator.markReleased(old.voiceId))
        assertFalse(allocator.finish(old.voiceId))
        assertEquals(VoiceLifecycle.ACTIVE, allocator.snapshot(replacement.voiceId)?.lifecycle)
    }

    @Test
    fun `stolen voice id becomes stale immediately`() {
        val allocator = VoiceAllocator(capacity = 2, sfxReserve = 1)
        val oldMusic = allocator.allocated(VoiceKind.MUSIC, 0)
        allocator.allocated(VoiceKind.SFX, 1)

        val replacement = allocator.allocated(VoiceKind.MUSIC, 2)

        assertEquals(oldMusic.voiceId, replacement.stolenVoiceId)
        assertFalse(allocator.markReleased(oldMusic.voiceId))
        assertNull(allocator.snapshot(oldMusic.voiceId))
        assertEquals(VoiceLifecycle.ACTIVE, allocator.snapshot(replacement.voiceId)?.lifecycle)
    }

    private fun VoiceAllocator.allocated(kind: VoiceKind, startedAtFrame: Long): VoiceAllocationResult.Allocated =
        assertIs<VoiceAllocationResult.Allocated>(allocate(kind, startedAtFrame))
}
