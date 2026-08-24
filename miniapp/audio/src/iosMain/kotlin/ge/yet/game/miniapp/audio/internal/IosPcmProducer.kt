package ge.yet.game.miniapp.audio.internal

import platform.Foundation.NSCondition
import platform.Foundation.NSDate
import platform.Foundation.NSLock
import platform.Foundation.NSThread

internal data class IosPcmBufferConfiguration(
    val producerQuantum: Int,
    val ringCapacity: Int,
    val startWatermark: Int,
    val targetWatermark: Int,
) {
    companion object {
        fun select(maximumFramesPerSlice: Int): IosPcmBufferConfiguration {
            require(maximumFramesPerSlice in 1..MAXIMUM_SAFE_RING_FRAMES)
            val quantum = maximumFramesPerSlice.coerceIn(MIN_PRODUCER_QUANTUM, MAX_PRODUCER_QUANTUM)
            val minimumCapacity = maxOf(BUFFERED_QUANTUM_COUNT * quantum, maximumFramesPerSlice)
            var capacity = 1
            while (capacity < minimumCapacity) capacity = capacity shl 1
            return IosPcmBufferConfiguration(
                producerQuantum = quantum,
                ringCapacity = capacity,
                startWatermark = minOf(capacity, maxOf(START_QUANTUM_COUNT * quantum, maximumFramesPerSlice)),
                targetWatermark = minOf(capacity, maxOf(TARGET_QUANTUM_COUNT * quantum, maximumFramesPerSlice)),
            )
        }
    }
}

internal data class IosPcmProducerDiagnostics(
    val producerWakeups: Long = 0,
    val renderFailures: Long = 0,
    val peakBufferedFrames: Long = 0,
)

internal interface IosPcmProducerSession {
    val callbackSource: IosPcmCallbackSource
    fun submit(command: AudioCommand): AudioRuntimeSubmitResult
    fun updatePolicy(policy: AudioSessionPolicy)
    fun resumeAndAwaitPrefill(): Boolean
    fun pauseAndReset()
    fun terminate()
    fun drainRuntimeDiagnostics(): AudioRuntimeDiagnosticsSnapshot
    fun drainProducerDiagnostics(): IosPcmProducerDiagnostics
}

internal fun interface IosPcmProducerFactory {
    fun create(
        sampleRate: Int,
        maximumFramesPerSlice: Int,
        rendererFactory: IosAudioRendererFactory,
    ): IosPcmProducerSession
}

internal class DefaultIosPcmProducer(
    private val sampleRate: Int,
    maximumFramesPerSlice: Int,
    rendererFactory: IosAudioRendererFactory,
    threadFactory: IosProducerThreadFactory? = IosProducerThreadFactory(::FoundationIosProducerThread),
) : IosPcmProducerSession {
    private val configuration = IosPcmBufferConfiguration.select(maximumFramesPerSlice)
    private val ring = StereoPcmRingBuffer(configuration.ringCapacity)
    override val callbackSource = IosPcmCallbackSource(ring)
    private val renderer = rendererFactory.create(sampleRate, configuration.producerQuantum)
    private val runtime = CompiledAudioRuntime(
        target = renderer,
        queueCapacity = COMMAND_QUEUE_CAPACITY,
        maxCommandsPerBlock = MAX_COMMANDS_PER_BLOCK,
    )
    private val left = FloatArray(configuration.producerQuantum)
    private val right = FloatArray(configuration.producerQuantum)
    private val commandLock = NSLock()
    private val stateLock = NSLock()
    private val progressCondition = NSCondition()
    private val worker = threadFactory?.create()
    private var state = ProducerState.Paused
    private var desiredPolicy = AudioSessionPolicy.Active
    private var appliedPolicy: AudioSessionPolicy? = null
    private var rendererDestroyed = false
    private var producerWakeups = 0L
    private var renderFailures = 0L
    private var peakBufferedFrames = 0L
    private var pumpInFlight = false

    internal val bufferedFrames: Int
        get() = ring.availableFrames

    init {
        require(sampleRate > 0)
        worker?.start(::runLoop)
    }

    override fun submit(command: AudioCommand): AudioRuntimeSubmitResult {
        if (stateLock.withLock { state.isTerminal }) return AudioRuntimeSubmitResult.RejectedDestroyed
        val result = commandLock.withLock {
            if (stateLock.withLock { state.isTerminal }) {
                AudioRuntimeSubmitResult.RejectedDestroyed
            } else {
                runtime.submit(command)
            }
        }
        if (result.accepted) worker?.signal()
        return result
    }

    override fun updatePolicy(policy: AudioSessionPolicy) {
        stateLock.withLock {
            if (!state.isTerminal) desiredPolicy = policy
        }
        worker?.signal()
    }

    override fun resumeAndAwaitPrefill(): Boolean {
        val resumed = stateLock.withLock {
            if (state.isTerminal) false else {
                state = ProducerState.Running
                true
            }
        }
        if (!resumed) return false
        val currentWorker = worker
        if (currentWorker == null) {
            while (ring.availableFrames < configuration.startWatermark) {
                if (!pumpOnce()) break
            }
        } else {
            currentWorker.signal()
            if (!awaitPrefill()) return false
        }
        return stateLock.withLock {
            state == ProducerState.Running && ring.availableFrames >= configuration.startWatermark
        }
    }

    override fun pauseAndReset() {
        stateLock.withLock {
            if (!state.isTerminal) state = ProducerState.Paused
        }
        worker?.signal()
        awaitProducerQuiescence()
        while (callbackSource.hasCallbackInFlight()) {
            NSThread.sleepForTimeInterval(QUIESCENCE_POLL_SECONDS)
        }
        ring.reset()
    }

    override fun terminate() {
        val shouldDestroy = stateLock.withLock {
            if (rendererDestroyed) {
                false
            } else {
                state = ProducerState.Terminated
                rendererDestroyed = true
                true
            }
        }
        if (!shouldDestroy) return
        worker?.let { currentWorker ->
            currentWorker.signal()
            while (!currentWorker.awaitTermination(TERMINATION_WAIT_SECONDS)) {
                // Renderer state must outlive its producer thread.
            }
        }
        try {
            renderer.destroy()
        } catch (_: Throwable) {
            stateLock.withLock { renderFailures = incrementSaturated(renderFailures) }
        }
        ring.reset()
    }

    override fun drainRuntimeDiagnostics(): AudioRuntimeDiagnosticsSnapshot =
        commandLock.withLock(runtime::drainDiagnostics)

    override fun drainProducerDiagnostics(): IosPcmProducerDiagnostics = stateLock.withLock {
        IosPcmProducerDiagnostics(
            producerWakeups = producerWakeups,
            renderFailures = renderFailures,
            peakBufferedFrames = peakBufferedFrames,
        ).also {
            producerWakeups = 0
            renderFailures = 0
            peakBufferedFrames = 0
        }
    }

    internal fun pumpOnce(): Boolean {
        val policy = stateLock.withLock {
            if (state != ProducerState.Running || ring.availableFrames >= configuration.targetWatermark) {
                return false
            }
            pumpInFlight = true
            producerWakeups = incrementSaturated(producerWakeups)
            desiredPolicy
        }
        return try {
            commandLock.withLock { runtime.consumeCommandsForBlockOrThrow() }
            if (runtime.isDestroyed) {
                stateLock.withLock {
                    state = ProducerState.Terminated
                    rendererDestroyed = true
                }
                false
            } else {
                if (policy != appliedPolicy) {
                    renderer.updatePolicy(policy)
                    appliedPolicy = policy
                }
                val frames = minOf(configuration.producerQuantum, ring.freeFrames)
                if (frames == 0) return false
                renderer.render(left, right, frames)
                check(ring.write(left, right, frames) == frames)
                val buffered = ring.availableFrames.toLong()
                stateLock.withLock {
                    if (buffered > peakBufferedFrames) peakBufferedFrames = buffered
                }
                true
            }
        } catch (_: Throwable) {
            left.fill(0f)
            right.fill(0f)
            stateLock.withLock {
                state = ProducerState.Failed
                renderFailures = incrementSaturated(renderFailures)
            }
            false
        } finally {
            stateLock.withLock { pumpInFlight = false }
            signalProgress()
        }
    }

    private fun runLoop() {
        while (stateLock.withLock { state != ProducerState.Terminated }) {
            if (!pumpOnce()) {
                worker?.awaitSignal(nextWaitSeconds())
            }
        }
    }

    private fun awaitPrefill(): Boolean {
        val deadline = deadlineAfter(PREFILL_TIMEOUT_SECONDS)
        progressCondition.lock()
        return try {
            while (ring.availableFrames < configuration.startWatermark) {
                if (stateLock.withLock { state != ProducerState.Running }) return false
                if (!progressCondition.waitUntilDate(deadline)) return false
            }
            true
        } finally {
            progressCondition.unlock()
        }
    }

    private fun awaitProducerQuiescence() {
        while (stateLock.withLock { pumpInFlight }) {
            NSThread.sleepForTimeInterval(QUIESCENCE_POLL_SECONDS)
        }
    }

    private fun nextWaitSeconds(): Double {
        val bufferedSeconds = ring.availableFrames.toDouble() / sampleRate.toDouble()
        return (bufferedSeconds / 2.0).coerceIn(MIN_WORKER_WAIT_SECONDS, MAX_WORKER_WAIT_SECONDS)
    }

    private fun signalProgress() {
        progressCondition.lock()
        try {
            progressCondition.broadcast()
        } finally {
            progressCondition.unlock()
        }
    }

    private enum class ProducerState {
        Paused,
        Running,
        Failed,
        Terminated,
        ;

        val isTerminal: Boolean get() = this == Failed || this == Terminated
    }
}

private inline fun <T> NSLock.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

private fun incrementSaturated(value: Long): Long = if (value == Long.MAX_VALUE) value else value + 1

private val AudioRuntimeSubmitResult.accepted: Boolean
    get() = when (this) {
        AudioRuntimeSubmitResult.Accepted,
        AudioRuntimeSubmitResult.Coalesced,
        AudioRuntimeSubmitResult.AcceptedAfterEviction,
        -> true
        AudioRuntimeSubmitResult.RejectedQueueFull,
        AudioRuntimeSubmitResult.RejectedDestroyed,
        -> false
    }

private fun deadlineAfter(timeoutSeconds: Double): NSDate = NSDate(
    timeIntervalSinceReferenceDate = NSDate().timeIntervalSinceReferenceDate + timeoutSeconds,
)

private const val MIN_PRODUCER_QUANTUM = 64
private const val MAX_PRODUCER_QUANTUM = 512
private const val BUFFERED_QUANTUM_COUNT = 8
private const val START_QUANTUM_COUNT = 3
private const val TARGET_QUANTUM_COUNT = 6
private const val MAXIMUM_SAFE_RING_FRAMES = 1 shl 29
private const val COMMAND_QUEUE_CAPACITY = 64
private const val MAX_COMMANDS_PER_BLOCK = 8
private const val PREFILL_TIMEOUT_SECONDS = 2.0
private const val TERMINATION_WAIT_SECONDS = 1.0
private const val QUIESCENCE_POLL_SECONDS = 0.001
private const val MIN_WORKER_WAIT_SECONDS = 0.001
private const val MAX_WORKER_WAIT_SECONDS = 0.010
