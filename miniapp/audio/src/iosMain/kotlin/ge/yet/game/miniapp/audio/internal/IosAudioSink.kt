package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.get
import kotlinx.cinterop.pointed
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.set
import platform.AVFAudio.AVAudioEngine
import platform.AVFAudio.AVAudioFormat
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.AVAudioSessionInterruptionOptionShouldResume
import platform.AVFAudio.AVAudioSessionInterruptionOptionKey
import platform.AVFAudio.AVAudioSessionInterruptionTypeBegan
import platform.AVFAudio.AVAudioSessionInterruptionTypeKey
import platform.AVFAudio.AVAudioSessionMediaServicesWereResetNotification
import platform.AVFAudio.AVAudioSessionModeDefault
import platform.AVFAudio.AVAudioSessionRouteChangeNotification
import platform.AVFAudio.AVAudioSourceNode
import platform.AVFAudio.sampleRate
import platform.AVFAudio.setActive
import platform.CoreAudioTypes.AudioBufferList
import platform.Foundation.NSLock
import platform.Foundation.NSCondition
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSNumber

internal sealed interface IosAudioEvent {
    data object InterruptionBegan : IosAudioEvent
    data class InterruptionEnded(val shouldResume: Boolean) : IosAudioEvent
    data object RouteChanged : IosAudioEvent
    data object MediaServicesReset : IosAudioEvent
}

internal fun interface IosAudioObservation {
    fun remove()
}

internal fun interface IosPcmBlockRenderer {
    fun render(left: FloatArray, right: FloatArray, frameCount: Int)

    fun recordCallbackFailure() = Unit
}

internal interface IosAudioEngine {
    fun start()
    fun pause()
    fun reset()
    fun stop()
    fun release()
}

internal interface IosAudioPlatform {
    val sampleRate: Int
    val maximumFramesPerSlice: Int
    fun configureSession()
    fun setSessionActive(active: Boolean)
    fun createEngine(sampleRate: Int, renderer: IosPcmBlockRenderer): IosAudioEngine
    fun observeEvents(listener: (IosAudioEvent) -> Unit): IosAudioObservation
}

internal interface IosAudioRenderer : AudioRuntimeCommandTarget {
    fun updatePolicy(policy: AudioSessionPolicy)
    fun render(left: FloatArray, right: FloatArray, frameCount: Int)
}

internal fun interface IosAudioRendererFactory {
    fun create(sampleRate: Int, frameCapacity: Int): IosAudioRenderer
}

internal class IosAudioSink(
    private val platform: IosAudioPlatform,
    private val rendererFactory: IosAudioRendererFactory = IosAudioRendererFactory(::DefaultIosAudioRenderer),
    private val producerFactory: IosPcmProducerFactory = IosPcmProducerFactory(::createDefaultIosPcmProducer),
) : PlatformAudioSink {
    private val lock = NSLock()
    private var activeSession: IosAudioSinkSession? = null

    override fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession = lock.withLock {
        activeSession?.release()
        IosAudioSinkSession(platform, rendererFactory, producerFactory).also { activeSession = it }
    }
}

private fun createDefaultIosPcmProducer(
    sampleRate: Int,
    maximumFramesPerSlice: Int,
    rendererFactory: IosAudioRendererFactory,
): IosPcmProducerSession = DefaultIosPcmProducer(sampleRate, maximumFramesPerSlice, rendererFactory)

@OptIn(ExperimentalAtomicApi::class)
private class IosAudioSinkSession(
    private val platform: IosAudioPlatform,
    rendererFactory: IosAudioRendererFactory,
    producerFactory: IosPcmProducerFactory,
) : PlatformAudioSinkSession {
    override val sampleRate: Int = platform.sampleRate
    private val producer = producerFactory.create(sampleRate, platform.maximumFramesPerSlice, rendererFactory)
    private val transitionCondition = NSCondition()
    private val backendFailures = AtomicLong(0)
    private var engine: IosAudioEngine
    private var observation: IosAudioObservation
    private var policy = AudioSessionPolicy.Active
    private var released = false
    private var outputRequested = false
    private var outputRunning = false
    private var sessionActive = false
    private var interrupted = false
    private var resumeBlocked = false
    private var transitionGeneration = 0L
    private var transitioning = false
    private var pendingRouteReset = false
    private var pendingMediaReset = false

    init {
        platform.configureSession()
        engine = platform.createEngine(sampleRate, producer.callbackSource)
        observation = platform.observeEvents(::handleEvent)
    }

    override fun updatePolicy(policy: AudioSessionPolicy) {
        if (transitionCondition.withLock { released }) return
        producer.updatePolicy(policy)
        requestReconcile {
            this.policy = policy
        }
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlayMusic(program), requestsOutput = true)

    override fun stopMusic(fadeFrames: Int): AudioRuntimeSubmitResult =
        submit(AudioCommand.StopMusic(fadeFrames), requestsOutput = false)

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlaySfx(program, name), requestsOutput = true)

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeSubmitResult =
        submit(AudioCommand.SetControl(name, value), requestsOutput = false)

    override fun release() {
        transitionCondition.lock()
        if (released) {
            transitionCondition.unlock()
            return
        }
        released = true
        outputRequested = false
        transitionGeneration += 1
        while (transitioning) transitionCondition.wait()
        transitionCondition.unlock()

        if (outputRunning) tryPlatform(engine::pause)
        outputRunning = false
        tryPlatform(engine::stop)
        producer.terminate()
        tryPlatform(engine::release)
        tryPlatform(observation::remove)
        deactivateSession()
    }

    override fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot {
        val common = producer.drainRuntimeDiagnostics()
        val callback = producer.callbackSource.drainDiagnostics()
        val produced = producer.drainProducerDiagnostics()
        return AudioRuntimeDiagnosticsSnapshot(
            validationRejections = common.validationRejections,
            queueOverflows = common.queueOverflows,
            forcedVoiceShedding = common.forcedVoiceShedding,
            callbackFailures = saturatedAdd(
                saturatedAdd(common.callbackFailures, callback.callbackFailures),
                saturatedAdd(produced.renderFailures, backendFailures.exchange(0)),
            ),
            underruns = saturatedAdd(common.underruns, callback.underrunEvents),
        )
    }

    private fun submit(command: AudioCommand, requestsOutput: Boolean): AudioRuntimeSubmitResult {
        if (transitionCondition.withLock { released }) return AudioRuntimeSubmitResult.RejectedDestroyed
        val result = producer.submit(command)
        if (requestsOutput && result.isAcceptedBySink) {
            requestReconcile {
                outputRequested = true
                resumeBlocked = false
            }
        }
        return result
    }

    private fun handleEvent(event: IosAudioEvent) {
        requestReconcile {
            when (event) {
            IosAudioEvent.InterruptionBegan -> {
                interrupted = true
            }
            is IosAudioEvent.InterruptionEnded -> {
                interrupted = false
                resumeBlocked = !event.shouldResume
            }
                IosAudioEvent.RouteChanged -> pendingRouteReset = true
                IosAudioEvent.MediaServicesReset -> pendingMediaReset = true
            }
        }
    }

    private inline fun requestReconcile(mutation: IosAudioSinkSession.() -> Unit) {
        transitionCondition.lock()
        if (released) {
            transitionCondition.unlock()
            return
        }
        mutation()
        transitionGeneration += 1
        if (transitioning) {
            transitionCondition.unlock()
            return
        }
        transitioning = true
        transitionCondition.unlock()
        reconcileLoop()
    }

    private fun reconcileLoop() {
        while (true) {
            val snapshot = transitionCondition.withLock {
                if (released) {
                    finishTransitionLocked()
                    return
                }
                TransitionSnapshot(
                    generation = transitionGeneration,
                    engine = engine,
                    shouldRun = shouldRunLocked(),
                    routeReset = pendingRouteReset,
                    mediaReset = pendingMediaReset,
                ).also {
                    pendingRouteReset = false
                    pendingMediaReset = false
                }
            }

            when {
                snapshot.mediaReset -> {
                    rebuildEngine(snapshot.engine)
                    continue
                }
                snapshot.routeReset -> {
                    resetRoute(snapshot.engine)
                    continue
                }
                snapshot.shouldRun -> startOutput(snapshot)
                else -> stopOutput(snapshot.engine)
            }

            val finished = transitionCondition.withLock {
                if (released || snapshot.generation == transitionGeneration) {
                    finishTransitionLocked()
                    true
                } else {
                    false
                }
            }
            if (finished) return
        }
    }

    private fun startOutput(snapshot: TransitionSnapshot) {
        if (outputRunning) return
        if (!activateSession()) return
        val prefilled = producer.resumeAndAwaitPrefill()
        val stillCurrent = transitionCondition.withLock {
            !released && snapshot.generation == transitionGeneration && engine === snapshot.engine && shouldRunLocked()
        }
        if (!prefilled || !stillCurrent) {
            producer.pauseAndReset()
            deactivateSession()
            if (!prefilled && stillCurrent) {
                transitionCondition.withLock { resumeBlocked = true }
            }
            return
        }
        if (!tryPlatform(snapshot.engine::start)) {
            producer.pauseAndReset()
            deactivateSession()
            transitionCondition.withLock { resumeBlocked = true }
            return
        }
        val committed = transitionCondition.withLock {
            if (!released && snapshot.generation == transitionGeneration && engine === snapshot.engine && shouldRunLocked()) {
                outputRunning = true
                true
            } else {
                false
            }
        }
        if (!committed) {
            tryPlatform(snapshot.engine::pause)
            producer.pauseAndReset()
            deactivateSession()
        }
    }

    private fun stopOutput(target: IosAudioEngine) {
        if (outputRunning) tryPlatform(target::pause)
        outputRunning = false
        producer.pauseAndReset()
        deactivateSession()
    }

    private fun resetRoute(target: IosAudioEngine) {
        if (outputRunning) tryPlatform(target::pause)
        outputRunning = false
        producer.pauseAndReset()
        tryPlatform(target::reset)
    }

    private fun rebuildEngine(target: IosAudioEngine) {
        if (outputRunning) tryPlatform(target::pause)
        outputRunning = false
        producer.pauseAndReset()
        tryPlatform(target::stop)
        tryPlatform(target::release)
        deactivateSession()
        if (!tryPlatform(platform::configureSession)) return
        val replacement = try {
            platform.createEngine(sampleRate, producer.callbackSource)
        } catch (_: Throwable) {
            incrementSaturated(backendFailures)
            return
        }
        val retained = transitionCondition.withLock {
            if (!released && engine === target) {
                engine = replacement
                true
            } else {
                false
            }
        }
        if (!retained) tryPlatform(replacement::release)
    }

    private fun shouldRunLocked(): Boolean =
        outputRequested && !policy.schedulingPaused && !interrupted && !resumeBlocked

    private fun finishTransitionLocked() {
        transitioning = false
        transitionCondition.broadcast()
    }

    private fun activateSession(): Boolean {
        if (sessionActive) return true
        if (!tryPlatform { platform.setSessionActive(true) }) return false
        sessionActive = true
        return true
    }

    private fun deactivateSession() {
        if (!sessionActive) return
        tryPlatform { platform.setSessionActive(false) }
        sessionActive = false
    }

    private inline fun tryPlatform(operation: () -> Unit): Boolean = try {
        operation()
        true
    } catch (_: Throwable) {
        incrementSaturated(backendFailures)
        false
    }

    private data class TransitionSnapshot(
        val generation: Long,
        val engine: IosAudioEngine,
        val shouldRun: Boolean,
        val routeReset: Boolean,
        val mediaReset: Boolean,
    )
}

private class DefaultIosAudioRenderer(
    sampleRate: Int,
    frameCapacity: Int,
) : IosAudioRenderer {
    private val delegate = RealtimeAudioRenderer(sampleRate, frameCapacity)

    override fun updatePolicy(policy: AudioSessionPolicy) = delegate.updatePolicy(policy)
    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) =
        delegate.render(left, right, frameCount)
    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeCommandOutcome = delegate.playMusic(program)
    override fun stopMusic(fadeFrames: Int): AudioRuntimeCommandOutcome = delegate.stopMusic(fadeFrames)
    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeCommandOutcome =
        delegate.playSfx(program, name)
    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome =
        delegate.setControl(name, value)
    override fun destroy(): AudioRuntimeCommandOutcome = delegate.destroy()
}

@OptIn(ExperimentalForeignApi::class)
internal class DefaultIosAudioPlatform : IosAudioPlatform {
    private val audioSession = AVAudioSession.sharedInstance()
    override val sampleRate: Int
        get() = audioSession.sampleRate.toInt().takeIf { it in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE }
            ?: DEFAULT_SAMPLE_RATE
    override val maximumFramesPerSlice: Int = DEFAULT_MAXIMUM_FRAMES_PER_SLICE

    override fun configureSession() {
        check(
            audioSession.setCategory(
                AVAudioSessionCategoryPlayback,
                mode = AVAudioSessionModeDefault,
                options = AVAudioSessionCategoryOptionMixWithOthers,
                error = null,
            ),
        ) { "Unable to configure AVAudioSession" }
    }

    override fun setSessionActive(active: Boolean) {
        check(audioSession.setActive(active, error = null)) { "Unable to change AVAudioSession activity" }
    }

    override fun createEngine(sampleRate: Int, renderer: IosPcmBlockRenderer): IosAudioEngine =
        FrameworkIosAudioEngine(sampleRate, maximumFramesPerSlice, renderer)

    override fun observeEvents(listener: (IosAudioEvent) -> Unit): IosAudioObservation {
        val center = NSNotificationCenter.defaultCenter
        val tokens = listOf(
            center.addObserverForName(AVAudioSessionInterruptionNotification, null, null) { notification ->
                val userInfo = notification?.userInfo
                val type = (userInfo?.get(AVAudioSessionInterruptionTypeKey) as? NSNumber)?.unsignedIntegerValue
                if (type == AVAudioSessionInterruptionTypeBegan) {
                    listener(IosAudioEvent.InterruptionBegan)
                } else {
                    val options = (userInfo?.get(AVAudioSessionInterruptionOptionKey) as? NSNumber)
                        ?.unsignedIntegerValue ?: 0uL
                    listener(
                        IosAudioEvent.InterruptionEnded(
                            shouldResume = options and AVAudioSessionInterruptionOptionShouldResume != 0uL,
                        ),
                    )
                }
            },
            center.addObserverForName(AVAudioSessionRouteChangeNotification, null, null) {
                listener(IosAudioEvent.RouteChanged)
            },
            center.addObserverForName(AVAudioSessionMediaServicesWereResetNotification, null, null) {
                listener(IosAudioEvent.MediaServicesReset)
            },
        )
        return IosAudioObservation { tokens.forEach(center::removeObserver) }
    }
}

@OptIn(ExperimentalForeignApi::class)
private class FrameworkIosAudioEngine(
    sampleRate: Int,
    frameCapacity: Int,
    private val renderer: IosPcmBlockRenderer,
) : IosAudioEngine {
    private val engine = AVAudioEngine()
    private val format = checkNotNull(
        AVAudioFormat(standardFormatWithSampleRate = sampleRate.toDouble(), channels = STEREO_CHANNEL_COUNT.toUInt()),
    )
    private val left = FloatArray(frameCapacity)
    private val right = FloatArray(frameCapacity)
    private val source = AVAudioSourceNode(format) { _, _, frameCount, outputData ->
        renderCallback(frameCount.toInt(), outputData)
    }
    private var released = false

    init {
        engine.attachNode(source)
        engine.connect(source, engine.mainMixerNode, format)
        engine.prepare()
    }

    override fun start() {
        check(!released)
        check(engine.startAndReturnError(null)) { "AVAudioEngine failed to start" }
    }

    override fun pause() {
        if (!released) engine.pause()
    }

    override fun reset() {
        if (!released) engine.reset()
    }

    override fun stop() {
        if (!released) engine.stop()
    }

    override fun release() {
        if (released) return
        released = true
        engine.stop()
        engine.disconnectNodeOutput(source)
        engine.detachNode(source)
    }

    private fun renderCallback(frameCount: Int, outputData: CPointer<AudioBufferList>?): Int = try {
        val output = checkNotNull(outputData)
        val buffers = output.pointed.mBuffers
        check(output.pointed.mNumberBuffers.toInt() >= STEREO_CHANNEL_COUNT)
        val leftOutput = checkNotNull(buffers[0].mData).reinterpret<FloatVar>()
        val rightOutput = checkNotNull(buffers[1].mData).reinterpret<FloatVar>()
        var offset = 0
        while (offset < frameCount) {
            val blockFrames = minOf(left.size, frameCount - offset)
            renderer.render(left, right, blockFrames)
            for (frame in 0 until blockFrames) {
                leftOutput[offset + frame] = left[frame]
                rightOutput[offset + frame] = right[frame]
            }
            offset += blockFrames
        }
        0
    } catch (_: Throwable) {
        try {
            clearOutput(outputData, frameCount)
        } catch (_: Throwable) {
            // The callback must contain malformed native buffers as well.
        }
        try {
            renderer.recordCallbackFailure()
        } catch (_: Throwable) {
            // Diagnostics are best effort and must never escape the callback.
        }
        0
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun clearOutput(outputData: CPointer<AudioBufferList>?, frameCount: Int) {
    val output = outputData ?: return
    val buffers = output.pointed.mBuffers
    val count = output.pointed.mNumberBuffers.toInt()
    for (bufferIndex in 0 until count) {
        val samples = buffers[bufferIndex].mData?.reinterpret<FloatVar>() ?: continue
        for (frame in 0 until frameCount) samples[frame] = 0f
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

private inline fun <T> NSCondition.withLock(block: () -> T): T {
    lock()
    return try {
        block()
    } finally {
        unlock()
    }
}

private val AudioRuntimeSubmitResult.isAcceptedBySink: Boolean
    get() = when (this) {
        AudioRuntimeSubmitResult.Accepted,
        AudioRuntimeSubmitResult.AcceptedAfterEviction,
        AudioRuntimeSubmitResult.Coalesced,
        -> true
        AudioRuntimeSubmitResult.RejectedQueueFull,
        AudioRuntimeSubmitResult.RejectedDestroyed,
        -> false
    }

@OptIn(ExperimentalAtomicApi::class)
private fun incrementSaturated(counter: AtomicLong) {
    while (true) {
        val current = counter.load()
        if (current == Long.MAX_VALUE || counter.compareAndSet(current, current + 1)) return
    }
}

private fun clear(left: FloatArray, right: FloatArray, frameCount: Int) {
    left.fill(0f, 0, frameCount)
    right.fill(0f, 0, frameCount)
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private const val DEFAULT_SAMPLE_RATE = 48_000
private const val MIN_SAMPLE_RATE = 8_000
private const val MAX_SAMPLE_RATE = 192_000
private const val DEFAULT_MAXIMUM_FRAMES_PER_SLICE = 4_096
private const val MIN_BLOCK_FRAMES = 64
private const val STEREO_CHANNEL_COUNT = 2
private const val COMMAND_QUEUE_CAPACITY = 64
private const val MAX_COMMANDS_PER_BLOCK = 8
