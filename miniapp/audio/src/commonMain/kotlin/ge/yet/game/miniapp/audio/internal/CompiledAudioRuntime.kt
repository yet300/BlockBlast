package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName

internal enum class AudioRuntimeCommandOutcome {
    APPLIED,
    VALIDATION_REJECTED,
    FORCED_VOICE_SHEDDING,
}

internal interface AudioRuntimeCommandTarget {
    fun playMusic(program: CompiledAudioProgram): AudioRuntimeCommandOutcome
    fun stopMusic(fadeFrames: Int): AudioRuntimeCommandOutcome
    fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeCommandOutcome
    fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome
    fun destroy(): AudioRuntimeCommandOutcome
}

internal enum class AudioRuntimeSubmitResult {
    Accepted,
    AcceptedAfterEviction,
    Coalesced,
    RejectedQueueFull,
    RejectedDestroyed,
}

internal data class AudioRuntimeDiagnosticsSnapshot(
    val validationRejections: Long,
    val queueOverflows: Long,
    val forcedVoiceShedding: Long,
    val callbackFailures: Long,
    val underruns: Long,
) {
    companion object {
        val Empty = AudioRuntimeDiagnosticsSnapshot(0, 0, 0, 0, 0)
    }
}

/**
 * Bounded command dispatcher for one realtime runtime. It is thread-confined by the
 * owning engine; cross-thread publication is supplied by the engine boundary.
 */
internal class CompiledAudioRuntime(
    private val target: AudioRuntimeCommandTarget,
    queueCapacity: Int,
    private val maxCommandsPerBlock: Int,
) {
    private val queue = AudioCommandQueue(queueCapacity)
    private val diagnostics = AudioRuntimeDiagnosticCounters()
    private var isClosing = false

    var isDestroyed: Boolean = false
        private set

    val pendingCommandCount: Int get() = queue.size

    init {
        require(maxCommandsPerBlock > 0)
    }

    fun submit(command: AudioCommand): AudioRuntimeSubmitResult {
        if (isClosing || isDestroyed) return AudioRuntimeSubmitResult.RejectedDestroyed
        val offerResult = queue.offer(command)
        if (command === AudioCommand.Destroy && offerResult != AudioCommandOfferResult.RejectedFull) {
            isClosing = true
        }
        return when (offerResult) {
            AudioCommandOfferResult.Accepted -> AudioRuntimeSubmitResult.Accepted
            AudioCommandOfferResult.Coalesced -> AudioRuntimeSubmitResult.Coalesced
            AudioCommandOfferResult.AcceptedAfterEviction -> {
                diagnostics.increment(AudioRuntimeDiagnostic.QUEUE_OVERFLOW)
                AudioRuntimeSubmitResult.AcceptedAfterEviction
            }
            AudioCommandOfferResult.RejectedFull -> {
                diagnostics.increment(AudioRuntimeDiagnostic.QUEUE_OVERFLOW)
                AudioRuntimeSubmitResult.RejectedQueueFull
            }
        }
    }

    fun consumeCommandsForBlock(): Int {
        if (isDestroyed) return 0
        var consumed = 0
        while (consumed < maxCommandsPerBlock) {
            val command = queue.poll() ?: break
            consumed += 1
            val outcome = try {
                dispatch(command)
            } catch (_: Throwable) {
                diagnostics.increment(AudioRuntimeDiagnostic.CALLBACK_FAILURE)
                null
            }
            if (outcome != null) diagnostics.record(outcome)
            if (command === AudioCommand.Destroy) {
                isDestroyed = true
                queue.clear()
                break
            }
        }
        return consumed
    }

    fun recordUnderrun() {
        diagnostics.increment(AudioRuntimeDiagnostic.UNDERRUN)
    }

    fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot = diagnostics.drain()

    private fun dispatch(command: AudioCommand): AudioRuntimeCommandOutcome = when (command) {
        is AudioCommand.PlayMusic -> target.playMusic(command.program)
        is AudioCommand.StopMusic -> target.stopMusic(command.fadeFrames)
        is AudioCommand.PlaySfx -> target.playSfx(command.program, command.name)
        is AudioCommand.SetControl -> target.setControl(command.name, command.value)
        AudioCommand.Destroy -> target.destroy()
    }
}

private enum class AudioRuntimeDiagnostic {
    VALIDATION_REJECTION,
    QUEUE_OVERFLOW,
    FORCED_VOICE_SHEDDING,
    CALLBACK_FAILURE,
    UNDERRUN,
}

private class AudioRuntimeDiagnosticCounters {
    private val values = LongArray(AudioRuntimeDiagnostic.entries.size)

    fun increment(diagnostic: AudioRuntimeDiagnostic) {
        val index = diagnostic.ordinal
        if (values[index] < Long.MAX_VALUE) values[index] += 1
    }

    fun record(outcome: AudioRuntimeCommandOutcome) {
        when (outcome) {
            AudioRuntimeCommandOutcome.APPLIED -> Unit
            AudioRuntimeCommandOutcome.VALIDATION_REJECTED -> increment(AudioRuntimeDiagnostic.VALIDATION_REJECTION)
            AudioRuntimeCommandOutcome.FORCED_VOICE_SHEDDING -> increment(AudioRuntimeDiagnostic.FORCED_VOICE_SHEDDING)
        }
    }

    fun drain(): AudioRuntimeDiagnosticsSnapshot {
        val snapshot = AudioRuntimeDiagnosticsSnapshot(
            validationRejections = values[AudioRuntimeDiagnostic.VALIDATION_REJECTION.ordinal],
            queueOverflows = values[AudioRuntimeDiagnostic.QUEUE_OVERFLOW.ordinal],
            forcedVoiceShedding = values[AudioRuntimeDiagnostic.FORCED_VOICE_SHEDDING.ordinal],
            callbackFailures = values[AudioRuntimeDiagnostic.CALLBACK_FAILURE.ordinal],
            underruns = values[AudioRuntimeDiagnostic.UNDERRUN.ordinal],
        )
        values.fill(0)
        return if (snapshot == AudioRuntimeDiagnosticsSnapshot.Empty) AudioRuntimeDiagnosticsSnapshot.Empty else snapshot
    }
}
