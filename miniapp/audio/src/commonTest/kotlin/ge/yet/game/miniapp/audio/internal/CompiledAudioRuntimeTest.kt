package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompiledAudioRuntimeTest {
    @Test
    fun `render block consumes only the configured command budget`() {
        val target = RecordingTarget()
        val runtime = CompiledAudioRuntime(target, queueCapacity = 8, maxCommandsPerBlock = 2)
        runtime.submit(control("first", 0.1f))
        runtime.submit(control("second", 0.2f))
        runtime.submit(control("third", 0.3f))

        assertEquals(2, runtime.consumeCommandsForBlock())
        assertEquals(listOf("first", "second"), target.controls)
        assertEquals(1, runtime.pendingCommandCount)

        assertEquals(1, runtime.consumeCommandsForBlock())
        assertEquals(listOf("first", "second", "third"), target.controls)
    }

    @Test
    fun `queue rejection and critical eviction increment overflow diagnostics`() {
        val runtime = CompiledAudioRuntime(RecordingTarget(), queueCapacity = 2, maxCommandsPerBlock = 2)

        assertEquals(AudioRuntimeSubmitResult.Accepted, runtime.submit(control("first", 0.1f)))
        assertEquals(AudioRuntimeSubmitResult.Accepted, runtime.submit(control("second", 0.2f)))
        assertEquals(AudioRuntimeSubmitResult.RejectedQueueFull, runtime.submit(control("third", 0.3f)))
        assertEquals(AudioRuntimeSubmitResult.AcceptedAfterEviction, runtime.submit(AudioCommand.StopMusic(16)))

        assertEquals(2L, runtime.drainDiagnostics().queueOverflows)
        assertEquals(AudioRuntimeDiagnosticsSnapshot.Empty, runtime.drainDiagnostics())
    }

    @Test
    fun `target outcomes become validation and shedding counters`() {
        val target = RecordingTarget(
            outcomes = mutableListOf(
                AudioRuntimeCommandOutcome.VALIDATION_REJECTED,
                AudioRuntimeCommandOutcome.FORCED_VOICE_SHEDDING,
            ),
        )
        val runtime = CompiledAudioRuntime(target, queueCapacity = 4, maxCommandsPerBlock = 4)
        runtime.submit(control("invalid", 2f))
        runtime.submit(control("crowded", 0.5f))

        runtime.consumeCommandsForBlock()
        val diagnostics = runtime.drainDiagnostics()

        assertEquals(1L, diagnostics.validationRejections)
        assertEquals(1L, diagnostics.forcedVoiceShedding)
    }

    @Test
    fun `target failure is contained and later commands still run`() {
        val target = RecordingTarget(throwOnCall = 1)
        val runtime = CompiledAudioRuntime(target, queueCapacity = 4, maxCommandsPerBlock = 4)
        runtime.submit(control("fails", 0.1f))
        runtime.submit(control("survives", 0.2f))

        assertEquals(2, runtime.consumeCommandsForBlock())

        assertEquals(listOf("survives"), target.controls)
        assertEquals(1L, runtime.drainDiagnostics().callbackFailures)
    }

    @Test
    fun `underruns accumulate without allocation and drain resets counters`() {
        val runtime = CompiledAudioRuntime(RecordingTarget(), queueCapacity = 2, maxCommandsPerBlock = 1)

        runtime.recordUnderrun()
        runtime.recordUnderrun()

        assertEquals(2L, runtime.drainDiagnostics().underruns)
        assertEquals(AudioRuntimeDiagnosticsSnapshot.Empty, runtime.drainDiagnostics())
    }

    @Test
    fun `platform callback failures accumulate outside command dispatch`() {
        val runtime = CompiledAudioRuntime(RecordingTarget(), queueCapacity = 2, maxCommandsPerBlock = 1)

        runtime.recordCallbackFailure()

        assertEquals(1L, runtime.drainDiagnostics().callbackFailures)
    }

    @Test
    fun `destroy runs once clears queued tail and rejects later submissions`() {
        val target = RecordingTarget()
        val runtime = CompiledAudioRuntime(target, queueCapacity = 4, maxCommandsPerBlock = 4)
        runtime.submit(control("before", 0.1f))
        runtime.submit(AudioCommand.Destroy)
        assertEquals(
            AudioRuntimeSubmitResult.RejectedDestroyed,
            runtime.submit(control("after", 0.2f)),
        )

        assertEquals(2, runtime.consumeCommandsForBlock())

        assertTrue(runtime.isDestroyed)
        assertEquals(0, runtime.pendingCommandCount)
        assertEquals(listOf("before"), target.controls)
        assertEquals(1, target.destroyCount)
        assertEquals(AudioRuntimeSubmitResult.RejectedDestroyed, runtime.submit(AudioCommand.Destroy))
        assertEquals(0, runtime.consumeCommandsForBlock())
        assertEquals(1, target.destroyCount)
    }

    private fun control(name: String, value: Float) =
        AudioCommand.SetControl(AudioControlName(name), value)

    private class RecordingTarget(
        private val outcomes: MutableList<AudioRuntimeCommandOutcome> = mutableListOf(),
        private val throwOnCall: Int? = null,
    ) : AudioRuntimeCommandTarget {
        val controls = mutableListOf<String>()
        var destroyCount = 0
        private var callCount = 0

        override fun playMusic(program: CompiledAudioProgram) = outcome()

        override fun stopMusic(fadeFrames: Int) = outcome()

        override fun playSfx(program: CompiledAudioProgram, name: SfxName) = outcome()

        override fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome {
            val outcome = outcome()
            controls += name.value
            return outcome
        }

        override fun destroy(): AudioRuntimeCommandOutcome {
            val outcome = outcome()
            destroyCount += 1
            return outcome
        }

        private fun outcome(): AudioRuntimeCommandOutcome {
            callCount += 1
            if (callCount == throwOnCall) error("synthetic callback failure")
            return if (outcomes.isEmpty()) {
                AudioRuntimeCommandOutcome.APPLIED
            } else {
                outcomes.removeAt(0)
            }
        }
    }
}
