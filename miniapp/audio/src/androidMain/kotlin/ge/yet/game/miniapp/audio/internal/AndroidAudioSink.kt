package ge.yet.game.miniapp.audio.internal

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Process
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.SfxName
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.roundToInt

internal enum class AndroidPcmEncoding(val bytesPerSample: Int) {
    Float(kotlin.Float.SIZE_BYTES),
    Pcm16(kotlin.Short.SIZE_BYTES),
}

internal data class AndroidAudioTrackConfiguration(
    val sampleRate: Int,
    val encoding: AndroidPcmEncoding,
    val bufferSizeBytes: Int,
) {
    companion object {
        fun select(platform: AndroidAudioPlatform): AndroidAudioTrackConfiguration {
            val sampleRate = platform.nativeOutputSampleRate()
                .takeIf { it in MIN_SAMPLE_RATE..MAX_SAMPLE_RATE }
                ?: DEFAULT_SAMPLE_RATE
            var bufferSizeBytes = AndroidAudioPlatform.UnsupportedBufferSize
            val encoding = AndroidPcmEncoding.entries.firstOrNull { candidate ->
                bufferSizeBytes = platform.minimumBufferSize(sampleRate, candidate)
                bufferSizeBytes > 0
            } ?: error("Android has no supported stereo PCM streaming format")
            return AndroidAudioTrackConfiguration(
                sampleRate = sampleRate,
                encoding = encoding,
                bufferSizeBytes = bufferSizeBytes,
            )
        }
    }
}

internal interface AndroidAudioPlatform {
    fun nativeOutputSampleRate(): Int
    fun minimumBufferSize(sampleRate: Int, encoding: AndroidPcmEncoding): Int
    fun createTrack(configuration: AndroidAudioTrackConfiguration): AndroidAudioTrack
    fun requestAudioFocus(listener: (AndroidAudioFocusChange) -> Unit): Boolean
    fun abandonAudioFocus()
    fun startWriter(name: String, block: () -> Unit): Thread

    companion object {
        const val UnsupportedBufferSize: Int = -2
    }
}

internal enum class AndroidAudioFocusChange { Gain, Loss, Duck }

internal interface AndroidAudioTrack {
    fun play()
    fun pause()
    fun flush()
    fun setVolume(value: Float)
    fun write(samples: FloatArray, sampleCount: Int): Int
    fun write(samples: ShortArray, sampleCount: Int): Int
    fun release()
}

internal class AndroidAudioSink(
    private val platform: AndroidAudioPlatform,
) : PlatformAudioSink {
    private val lock = ReentrantLock()
    private var activeSession: AndroidAudioSinkSession? = null

    override fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession = lock.withLock {
        activeSession?.release()
        val (configuration, track) = createTrackWithFallback(platform)
        AndroidAudioSinkSession(platform, configuration, track).also { activeSession = it }
    }
}

private class AndroidAudioSinkSession(
    private val platform: AndroidAudioPlatform,
    private val configuration: AndroidAudioTrackConfiguration,
    private val track: AndroidAudioTrack,
) : PlatformAudioSinkSession {
    override val sampleRate: Int = configuration.sampleRate
    private val frameCapacity = (
        configuration.bufferSizeBytes / (STEREO_CHANNEL_COUNT * configuration.encoding.bytesPerSample)
        ).coerceAtLeast(MIN_BLOCK_FRAMES)
    private val renderer = RealtimeAudioRenderer(sampleRate, frameCapacity)
    private val runtime = CompiledAudioRuntime(
        target = renderer,
        queueCapacity = COMMAND_QUEUE_CAPACITY,
        maxCommandsPerBlock = MAX_COMMANDS_PER_BLOCK,
    )
    private val commandLock = ReentrantLock()
    private val running = AtomicBoolean(true)
    private val released = AtomicBoolean(false)
    private val callbackFailures = AtomicLong()
    private val underruns = AtomicLong()
    private val outputStarted = AtomicBoolean(false)
    private val left = FloatArray(frameCapacity)
    private val right = FloatArray(frameCapacity)
    private val floatInterleaved = FloatArray(frameCapacity * STEREO_CHANNEL_COUNT)
    private val pcm16Interleaved = ShortArray(frameCapacity * STEREO_CHANNEL_COUNT)
    @Volatile private var policy = AudioSessionPolicy.Active
    @Volatile private var focus = AndroidAudioFocusChange.Gain
    private val writer: Thread

    init {
        writer = platform.startWriter("miniapp-audio-writer", ::writeLoop)
    }

    override fun updatePolicy(policy: AudioSessionPolicy) {
        if (!released.get()) this.policy = policy
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlayMusic(program))

    override fun stopMusic(fadeFrames: Int): AudioRuntimeSubmitResult =
        submit(AudioCommand.StopMusic(fadeFrames))

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeSubmitResult =
        submit(AudioCommand.PlaySfx(program, name))

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeSubmitResult =
        submit(AudioCommand.SetControl(name, value))

    override fun release() {
        if (!released.compareAndSet(false, true)) return
        running.set(false)
        if (outputStarted.get()) {
            tryPlatform(track::pause)
            tryPlatform(track::flush)
        }
        writer.interrupt()
        if (Thread.currentThread() !== writer) joinWriterPreservingInterruption()
    }

    override fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot = commandLock.withLock {
        val common = runtime.drainDiagnostics()
        AudioRuntimeDiagnosticsSnapshot(
            validationRejections = common.validationRejections,
            queueOverflows = common.queueOverflows,
            forcedVoiceShedding = common.forcedVoiceShedding,
            callbackFailures = saturatedAdd(common.callbackFailures, callbackFailures.getAndSet(0)),
            underruns = saturatedAdd(common.underruns, underruns.getAndSet(0)),
        )
    }

    private fun submit(command: AudioCommand): AudioRuntimeSubmitResult {
        if (released.get()) return AudioRuntimeSubmitResult.RejectedDestroyed
        return commandLock.withLock {
            if (released.get()) AudioRuntimeSubmitResult.RejectedDestroyed else runtime.submit(command)
        }
    }

    private fun writeLoop() {
        var playing = false
        var focusHeld = false
        var appliedFocus: AndroidAudioFocusChange? = null
        var appliedPolicy: AudioSessionPolicy? = null
        try {
            while (running.get()) {
                if (commandLock.tryLock()) {
                    try {
                        runtime.consumeCommandsForBlock()
                    } finally {
                        commandLock.unlock()
                    }
                }
                val currentPolicy = policy
                val currentFocus = focus
                val shouldPause = currentPolicy.schedulingPaused || currentFocus == AndroidAudioFocusChange.Loss
                if (shouldPause) {
                    if (playing) {
                        track.pause()
                        track.flush()
                        playing = false
                        outputStarted.set(false)
                    }
                    if (currentPolicy.schedulingPaused && focusHeld) {
                        platform.abandonAudioFocus()
                        focusHeld = false
                        focus = AndroidAudioFocusChange.Gain
                        appliedFocus = null
                    }
                    Thread.sleep(PAUSED_POLL_MILLIS)
                    continue
                }

                if (currentPolicy != appliedPolicy) {
                    renderer.updatePolicy(currentPolicy)
                    appliedPolicy = currentPolicy
                }
                if (!renderer.hasActiveAudio) {
                    if (playing) {
                        track.pause()
                        track.flush()
                        playing = false
                        outputStarted.set(false)
                    }
                    if (focusHeld) {
                        platform.abandonAudioFocus()
                        focusHeld = false
                    }
                    Thread.sleep(PAUSED_POLL_MILLIS)
                    continue
                }
                if (!focusHeld) {
                    focus = AndroidAudioFocusChange.Gain
                    focusHeld = platform.requestAudioFocus { change ->
                        if (!released.get()) focus = change
                    }
                    appliedFocus = null
                    if (!focusHeld) {
                        Thread.sleep(PAUSED_POLL_MILLIS)
                        continue
                    }
                }
                if (currentFocus != appliedFocus) {
                    track.setVolume(if (currentFocus == AndroidAudioFocusChange.Duck) DUCK_VOLUME else 1f)
                    appliedFocus = currentFocus
                }
                if (!playing) {
                    if (!running.get()) continue
                    outputStarted.set(true)
                    if (!running.get()) {
                        outputStarted.set(false)
                        continue
                    }
                    track.play()
                    playing = true
                }
                renderer.render(left, right, frameCapacity)
                val written = when (configuration.encoding) {
                    AndroidPcmEncoding.Float -> writeFloatBlock()
                    AndroidPcmEncoding.Pcm16 -> writePcm16Block()
                }
                if (written < frameCapacity * STEREO_CHANNEL_COUNT) incrementSaturated(underruns)
            }
        } catch (_: InterruptedException) {
            // Normal synchronous release path.
        } catch (_: Throwable) {
            incrementSaturated(callbackFailures)
        } finally {
            tryPlatform { if (playing) track.pause() }
            outputStarted.set(false)
            tryPlatform(track::flush)
            tryPlatform(renderer::destroy)
            tryPlatform(track::release)
            if (focusHeld) tryPlatform(platform::abandonAudioFocus)
        }
    }

    private fun writeFloatBlock(): Int {
        interleaveFloat(left, right, floatInterleaved, frameCapacity)
        return track.write(floatInterleaved, frameCapacity * STEREO_CHANNEL_COUNT)
    }

    private fun writePcm16Block(): Int {
        interleavePcm16(left, right, pcm16Interleaved, frameCapacity)
        return track.write(pcm16Interleaved, frameCapacity * STEREO_CHANNEL_COUNT)
    }

    private inline fun tryPlatform(operation: () -> Unit) {
        try {
            operation()
        } catch (_: Throwable) {
            incrementSaturated(callbackFailures)
        }
    }

    private fun joinWriterPreservingInterruption() {
        var interrupted = false
        while (writer.isAlive) {
            try {
                writer.join()
            } catch (_: InterruptedException) {
                interrupted = true
                writer.interrupt()
            }
        }
        if (interrupted) Thread.currentThread().interrupt()
    }
}

internal class DefaultAndroidAudioPlatform(context: Context) : AndroidAudioPlatform {
    private val audioManager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var focusRequest: AudioFocusRequest? = null
    private var legacyFocusListener: AudioManager.OnAudioFocusChangeListener? = null

    override fun nativeOutputSampleRate(): Int = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC)

    override fun minimumBufferSize(sampleRate: Int, encoding: AndroidPcmEncoding): Int =
        AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, encoding.androidValue)

    override fun createTrack(configuration: AndroidAudioTrackConfiguration): AndroidAudioTrack {
        val delegate = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(configuration.sampleRate)
                    .setEncoding(configuration.encoding.androidValue)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(configuration.bufferSizeBytes)
            .build()
        if (delegate.state != AudioTrack.STATE_INITIALIZED) {
            delegate.release()
            error("AudioTrack failed to initialize")
        }
        return FrameworkAndroidAudioTrack(delegate)
    }

    override fun requestAudioFocus(listener: (AndroidAudioFocusChange) -> Unit): Boolean {
        val frameworkListener = AudioManager.OnAudioFocusChangeListener { listener(it.toFocusChange()) }
        legacyFocusListener = frameworkListener
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes())
                .setOnAudioFocusChangeListener(frameworkListener)
                .setWillPauseWhenDucked(false)
                .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(frameworkListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
        }
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let(audioManager::abandonAudioFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            legacyFocusListener?.let(audioManager::abandonAudioFocus)
        }
        focusRequest = null
        legacyFocusListener = null
    }

    override fun startWriter(name: String, block: () -> Unit): Thread = Thread(
        {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            block()
        },
        name,
    ).also(Thread::start)
}

private class FrameworkAndroidAudioTrack(private val delegate: AudioTrack) : AndroidAudioTrack {
    override fun play() = delegate.play()
    override fun pause() = delegate.pause()
    override fun flush() = delegate.flush()
    override fun setVolume(value: Float) { delegate.setVolume(value) }
    override fun write(samples: FloatArray, sampleCount: Int): Int =
        delegate.write(samples, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
    override fun write(samples: ShortArray, sampleCount: Int): Int =
        delegate.write(samples, 0, sampleCount, AudioTrack.WRITE_BLOCKING)
    override fun release() = delegate.release()
}

private fun createTrackWithFallback(
    platform: AndroidAudioPlatform,
): Pair<AndroidAudioTrackConfiguration, AndroidAudioTrack> {
    val preferred = AndroidAudioTrackConfiguration.select(platform)
    var lastFailure: Throwable? = null
    val candidates = buildList {
        add(preferred)
        AndroidPcmEncoding.entries.dropWhile { it != preferred.encoding }.drop(1).forEach { encoding ->
            val bufferSize = platform.minimumBufferSize(preferred.sampleRate, encoding)
            if (bufferSize > 0) add(AndroidAudioTrackConfiguration(preferred.sampleRate, encoding, bufferSize))
        }
    }
    for (configuration in candidates) {
        try {
            return configuration to platform.createTrack(configuration)
        } catch (failure: Exception) {
            lastFailure = failure
        }
    }
    throw IllegalStateException("Android has no usable stereo PCM streaming format", lastFailure)
}

private fun interleaveFloat(left: FloatArray, right: FloatArray, output: FloatArray, frameCount: Int) {
    for (frame in 0 until frameCount) {
        output[frame * 2] = left[frame].coerceIn(-1f, 1f)
        output[frame * 2 + 1] = right[frame].coerceIn(-1f, 1f)
    }
}

private fun interleavePcm16(left: FloatArray, right: FloatArray, output: ShortArray, frameCount: Int) {
    for (frame in 0 until frameCount) {
        output[frame * 2] = (left[frame].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
        output[frame * 2 + 1] = (right[frame].coerceIn(-1f, 1f) * Short.MAX_VALUE).roundToInt().toShort()
    }
}

private val AndroidPcmEncoding.androidValue: Int
    get() = when (this) {
        AndroidPcmEncoding.Float -> AudioFormat.ENCODING_PCM_FLOAT
        AndroidPcmEncoding.Pcm16 -> AudioFormat.ENCODING_PCM_16BIT
    }

private fun Int.toFocusChange(): AndroidAudioFocusChange = when (this) {
    AudioManager.AUDIOFOCUS_GAIN -> AndroidAudioFocusChange.Gain
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AndroidAudioFocusChange.Duck
    else -> AndroidAudioFocusChange.Loss
}

private fun audioAttributes(): AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_GAME)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

private fun incrementSaturated(counter: AtomicLong) {
    while (true) {
        val current = counter.get()
        if (current == Long.MAX_VALUE || counter.compareAndSet(current, current + 1)) return
    }
}

private fun saturatedAdd(left: Long, right: Long): Long =
    if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

private const val DEFAULT_SAMPLE_RATE = 48_000
private const val MIN_SAMPLE_RATE = 8_000
private const val MAX_SAMPLE_RATE = 192_000
private const val STEREO_CHANNEL_COUNT = 2
private const val MIN_BLOCK_FRAMES = 64
private const val COMMAND_QUEUE_CAPACITY = 64
private const val MAX_COMMANDS_PER_BLOCK = 8
private const val PAUSED_POLL_MILLIS = 5L
private const val DUCK_VOLUME = 0.2f
