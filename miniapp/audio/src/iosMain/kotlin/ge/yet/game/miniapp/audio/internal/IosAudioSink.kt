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
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVAudioSessionCategoryOptionMixWithOthers
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
) : PlatformAudioSink {
    private val lock = NSLock()
    private var activeSession: IosAudioSinkSession? = null

    override fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession = lock.withLock {
        activeSession?.release()
        IosAudioSinkSession(platform, rendererFactory).also { activeSession = it }
    }
}

@OptIn(ExperimentalAtomicApi::class)
private class IosAudioSinkSession(
    private val platform: IosAudioPlatform,
    rendererFactory: IosAudioRendererFactory,
) : PlatformAudioSinkSession, IosPcmBlockRenderer {
    override val sampleRate: Int = platform.sampleRate
    private val frameCapacity = platform.maximumFramesPerSlice.coerceAtLeast(MIN_BLOCK_FRAMES)
    private val renderer = rendererFactory.create(sampleRate, frameCapacity)
    private val runtime = CompiledAudioRuntime(
        target = renderer,
        queueCapacity = COMMAND_QUEUE_CAPACITY,
        maxCommandsPerBlock = MAX_COMMANDS_PER_BLOCK,
    )
    private val lock = NSLock()
    private val callbackFailures = AtomicLong(0)
    private val underruns = AtomicLong(0)
    private var engine: IosAudioEngine
    private var observation: IosAudioObservation
    private var policy = AudioSessionPolicy.Active
    private var released = false
    private var outputRequested = false
    private var outputRunning = false
    private var sessionActive = false
    private var interrupted = false
    private var resumeBlocked = false

    init {
        platform.configureSession()
        engine = platform.createEngine(sampleRate, this)
        observation = platform.observeEvents(::handleEvent)
    }

    override fun updatePolicy(policy: AudioSessionPolicy) = lock.withLock {
        if (released) return@withLock
        this.policy = policy
        renderer.updatePolicy(policy)
        synchronizeOutput()
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlayMusic(program), requestsOutput = true)

    override fun stopMusic(fadeFrames: Int): AudioRuntimeSubmitResult =
        submit(AudioCommand.StopMusic(fadeFrames), requestsOutput = false)

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlaySfx(program, name), requestsOutput = true)

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeSubmitResult =
        submit(AudioCommand.SetControl(name, value), requestsOutput = false)

    override fun release() = lock.withLock {
        if (released) return@withLock
        released = true
        outputRequested = false
        if (outputRunning) tryPlatform(engine::pause)
        outputRunning = false
        tryPlatform(engine::stop)
        tryPlatform(engine::release)
        tryPlatform(observation::remove)
        deactivateSession()
        tryPlatform(renderer::destroy)
    }

    override fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot = lock.withLock {
        val common = runtime.drainDiagnostics()
        AudioRuntimeDiagnosticsSnapshot(
            validationRejections = common.validationRejections,
            queueOverflows = common.queueOverflows,
            forcedVoiceShedding = common.forcedVoiceShedding,
            callbackFailures = saturatedAdd(common.callbackFailures, callbackFailures.exchange(0)),
            underruns = saturatedAdd(common.underruns, underruns.exchange(0)),
        )
    }

    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        clear(left, right, frameCount)
        if (!lock.tryLock()) {
            incrementSaturated(underruns)
            return
        }
        try {
            if (released || !outputRunning || policy.schedulingPaused || interrupted) return
            runtime.consumeCommandsForBlock()
            renderer.render(left, right, frameCount)
        } catch (_: Throwable) {
            clear(left, right, frameCount)
            incrementSaturated(callbackFailures)
        } finally {
            lock.unlock()
        }
    }

    override fun recordCallbackFailure() {
        incrementSaturated(callbackFailures)
    }

    private fun submit(command: AudioCommand, requestsOutput: Boolean): AudioRuntimeSubmitResult = lock.withLock {
        if (released) return@withLock AudioRuntimeSubmitResult.RejectedDestroyed
        val result = runtime.submit(command)
        if (requestsOutput && result == AudioRuntimeSubmitResult.Accepted) {
            outputRequested = true
            resumeBlocked = false
            synchronizeOutput()
        }
        result
    }

    private fun handleEvent(event: IosAudioEvent) = lock.withLock {
        if (released) return@withLock
        when (event) {
            IosAudioEvent.InterruptionBegan -> {
                interrupted = true
                synchronizeOutput()
            }
            is IosAudioEvent.InterruptionEnded -> {
                interrupted = false
                resumeBlocked = !event.shouldResume
                synchronizeOutput()
            }
            IosAudioEvent.RouteChanged -> restartAfterRouteChange()
            IosAudioEvent.MediaServicesReset -> rebuildAfterMediaServicesReset()
        }
    }

    private fun synchronizeOutput() {
        val shouldRun = outputRequested && !policy.schedulingPaused && !interrupted && !resumeBlocked
        if (shouldRun == outputRunning) return
        if (shouldRun) {
            if (!activateSession()) return
            if (tryPlatform(engine::start)) {
                outputRunning = true
            } else {
                deactivateSession()
            }
        } else {
            if (outputRunning) tryPlatform(engine::pause)
            outputRunning = false
            deactivateSession()
        }
    }

    private fun restartAfterRouteChange() {
        val shouldRestart = outputRunning
        if (outputRunning) tryPlatform(engine::pause)
        outputRunning = false
        tryPlatform(engine::reset)
        if (shouldRestart && tryPlatform(engine::start)) outputRunning = true
    }

    private fun rebuildAfterMediaServicesReset() {
        val shouldRestart = outputRequested && !policy.schedulingPaused && !interrupted && !resumeBlocked
        if (outputRunning) tryPlatform(engine::pause)
        outputRunning = false
        tryPlatform(engine::stop)
        tryPlatform(engine::release)
        sessionActive = false
        tryPlatform(platform::configureSession)
        engine = platform.createEngine(sampleRate, this)
        if (shouldRestart) {
            if (activateSession() && tryPlatform(engine::start)) outputRunning = true
        }
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
        incrementSaturated(callbackFailures)
        false
    }
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
                AVAudioSessionCategoryAmbient,
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
