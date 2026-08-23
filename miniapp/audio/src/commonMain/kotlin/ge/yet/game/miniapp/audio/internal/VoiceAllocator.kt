package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioMobileBudget

internal enum class VoiceKind { MUSIC, SFX }

internal enum class VoiceLifecycle { ACTIVE, RELEASED }

internal data class VoiceAllocationSnapshot(
    val voiceId: Long,
    val kind: VoiceKind,
    val lifecycle: VoiceLifecycle,
    val level: Float,
    val startedAtFrame: Long,
)

internal sealed interface VoiceAllocationResult {
    data class Allocated(
        val voiceId: Long,
        val stolenVoiceId: Long?,
    ) : VoiceAllocationResult

    data object Rejected : VoiceAllocationResult
}

internal class VoiceAllocator(
    private val capacity: Int = AudioMobileBudget.MAX_VOICES,
    private val sfxReserve: Int = AudioMobileBudget.SFX_RESERVED_VOICES,
) {
    private val occupied: BooleanArray
    private val voiceIds: LongArray
    private val kinds: Array<VoiceKind?>
    private val lifecycles: Array<VoiceLifecycle?>
    private val levels: FloatArray
    private val startedAtFrames: LongArray
    private val musicLimit: Int
    private var nextVoiceId = 1L

    var voiceCount: Int = 0
        private set

    var musicVoiceCount: Int = 0
        private set

    init {
        require(capacity in 2..AudioMobileBudget.MAX_VOICES)
        require(sfxReserve in 1 until capacity)
        musicLimit = capacity - sfxReserve
        occupied = BooleanArray(capacity)
        voiceIds = LongArray(capacity)
        kinds = arrayOfNulls(capacity)
        lifecycles = arrayOfNulls(capacity)
        levels = FloatArray(capacity)
        startedAtFrames = LongArray(capacity)
    }

    fun allocate(kind: VoiceKind, startedAtFrame: Long): VoiceAllocationResult {
        require(startedAtFrame >= 0)
        val freeSlot = findFreeSlot()
        val target = when (kind) {
            VoiceKind.MUSIC if musicVoiceCount < musicLimit && freeSlot >= 0 -> freeSlot
            VoiceKind.MUSIC -> findVictim(VoiceKind.MUSIC)
            VoiceKind.SFX if freeSlot >= 0 -> freeSlot
            VoiceKind.SFX -> findVictim(VoiceKind.SFX)
        }
        if (target < 0) return VoiceAllocationResult.Rejected

        val stolenVoiceId = voiceIds[target].takeIf { occupied[target] }
        val previousKind = kinds[target]
        if (!occupied[target]) voiceCount += 1
        if (previousKind == VoiceKind.MUSIC && kind != VoiceKind.MUSIC) musicVoiceCount -= 1
        if (previousKind != VoiceKind.MUSIC && kind == VoiceKind.MUSIC) musicVoiceCount += 1

        val voiceId = nextVoiceId()
        occupied[target] = true
        voiceIds[target] = voiceId
        kinds[target] = kind
        lifecycles[target] = VoiceLifecycle.ACTIVE
        levels[target] = 1f
        startedAtFrames[target] = startedAtFrame
        return VoiceAllocationResult.Allocated(voiceId, stolenVoiceId)
    }

    fun updateLevel(voiceId: Long, level: Float): Boolean {
        require(level.isFinite() && level in 0f..1f)
        val slot = findVoice(voiceId)
        if (slot < 0) return false
        levels[slot] = level
        return true
    }

    fun markReleased(voiceId: Long): Boolean {
        val slot = findVoice(voiceId)
        if (slot < 0) return false
        lifecycles[slot] = VoiceLifecycle.RELEASED
        return true
    }

    fun finish(voiceId: Long): Boolean {
        val slot = findVoice(voiceId)
        if (slot < 0) return false
        if (kinds[slot] == VoiceKind.MUSIC) musicVoiceCount -= 1
        occupied[slot] = false
        kinds[slot] = null
        lifecycles[slot] = null
        levels[slot] = 0f
        startedAtFrames[slot] = 0L
        voiceCount -= 1
        return true
    }

    fun snapshot(voiceId: Long): VoiceAllocationSnapshot? {
        val slot = findVoice(voiceId)
        if (slot < 0) return null
        return VoiceAllocationSnapshot(
            voiceId = voiceIds[slot],
            kind = kinds[slot] ?: error("Occupied voice requires a kind"),
            lifecycle = lifecycles[slot] ?: error("Occupied voice requires a lifecycle"),
            level = levels[slot],
            startedAtFrame = startedAtFrames[slot],
        )
    }

    private fun findFreeSlot(): Int {
        for (slot in 0 until capacity) if (!occupied[slot]) return slot
        return -1
    }

    private fun findVoice(voiceId: Long): Int {
        for (slot in 0 until capacity) {
            if (occupied[slot] && voiceIds[slot] == voiceId) return slot
        }
        return -1
    }

    private fun findVictim(requestedKind: VoiceKind): Int {
        var best = -1
        for (slot in 0 until capacity) {
            if (!occupied[slot]) continue
            if (requestedKind == VoiceKind.MUSIC && kinds[slot] != VoiceKind.MUSIC) continue
            if (best < 0 || isBetterVictim(slot, best)) best = slot
        }
        return best
    }

    private fun isBetterVictim(candidate: Int, current: Int): Boolean {
        val candidateReleased = lifecycles[candidate] == VoiceLifecycle.RELEASED
        val currentReleased = lifecycles[current] == VoiceLifecycle.RELEASED
        if (candidateReleased != currentReleased) return candidateReleased
        if (levels[candidate] != levels[current]) return levels[candidate] < levels[current]
        if (startedAtFrames[candidate] != startedAtFrames[current]) {
            return startedAtFrames[candidate] < startedAtFrames[current]
        }
        val candidateMusic = kinds[candidate] == VoiceKind.MUSIC
        val currentMusic = kinds[current] == VoiceKind.MUSIC
        if (candidateMusic != currentMusic) return candidateMusic
        return voiceIds[candidate] < voiceIds[current]
    }

    private fun nextVoiceId(): Long {
        check(nextVoiceId < Long.MAX_VALUE) { "Audio voice ID space exhausted" }
        return nextVoiceId++
    }
}
