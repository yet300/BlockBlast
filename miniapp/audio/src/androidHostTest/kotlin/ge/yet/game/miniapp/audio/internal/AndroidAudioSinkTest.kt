package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.compile
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AndroidAudioSinkTest {
    @Test
    fun `configuration uses native rate and minimum float stereo buffer`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 44_100,
            minimumBuffers = mapOf(AndroidPcmEncoding.Float to 3_840),
        )

        val configuration = AndroidAudioTrackConfiguration.select(platform)

        assertEquals(44_100, configuration.sampleRate)
        assertEquals(AndroidPcmEncoding.Float, configuration.encoding)
        assertEquals(3_840, configuration.bufferSizeBytes)
        assertEquals(
            listOf(MinimumBufferRequest(44_100, AndroidPcmEncoding.Float)),
            platform.minimumBufferRequests,
        )
    }

    @Test
    fun `configuration falls back to pcm16 when float is unsupported`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 48_000,
            minimumBuffers = mapOf(
                AndroidPcmEncoding.Float to AndroidAudioPlatform.UnsupportedBufferSize,
                AndroidPcmEncoding.Pcm16 to 4_096,
            ),
        )

        val configuration = AndroidAudioTrackConfiguration.select(platform)

        assertEquals(AndroidPcmEncoding.Pcm16, configuration.encoding)
        assertEquals(4_096, configuration.bufferSizeBytes)
        assertEquals(
            listOf(
                MinimumBufferRequest(48_000, AndroidPcmEncoding.Float),
                MinimumBufferRequest(48_000, AndroidPcmEncoding.Pcm16),
            ),
            platform.minimumBufferRequests,
        )
    }

    @Test
    fun `track creation failure retries pcm16`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 48_000,
            minimumBuffers = mapOf(
                AndroidPcmEncoding.Float to 4_096,
                AndroidPcmEncoding.Pcm16 to 2_048,
            ),
            rejectedTrackEncodings = setOf(AndroidPcmEncoding.Float),
        )
        val session = AndroidAudioSink(platform).openSession(MiniAppId("game.audio-test"), 1L)

        assertEquals(
            listOf(AndroidPcmEncoding.Float, AndroidPcmEncoding.Pcm16),
            platform.trackCreationRequests.map(AndroidAudioTrackConfiguration::encoding),
        )
        assertEquals(AndroidPcmEncoding.Pcm16, platform.output.configuration.encoding)

        session.playMusic(compiledAndroidTone())
        assertTrue(platform.output.nonZeroPcm16.await(2, TimeUnit.SECONDS))
        assertTrue(platform.output.lastPcm16.any { it != 0.toShort() })
        session.release()
    }

    @Test
    fun `opening a silent session does not play or capture audio focus`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 8_000,
            minimumBuffers = mapOf(AndroidPcmEncoding.Float to 1_024),
        )
        val session = AndroidAudioSink(platform).openSession(MiniAppId("game.audio-test"), 5L)

        assertFalse(platform.output.firstPlay.await(100, TimeUnit.MILLISECONDS))
        assertEquals(0, platform.requestFocusCount)

        session.release()
        assertEquals(0, platform.abandonFocusCount)
        assertEquals(0L, session.drainDiagnostics().callbackFailures)
    }

    @Test
    fun `focus loss pauses and flushes while gain resumes the same session`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 8_000,
            minimumBuffers = mapOf(AndroidPcmEncoding.Float to 1_024),
        )
        val session = AndroidAudioSink(platform).openSession(MiniAppId("game.audio-test"), 3L)
        session.playMusic(compiledAndroidTone())
        assertTrue(platform.output.firstWrite.await(2, TimeUnit.SECONDS))

        platform.dispatchFocus(AndroidAudioFocusChange.Loss)
        assertTrue(platform.output.pause.await(2, TimeUnit.SECONDS))
        assertTrue(platform.output.flush.await(2, TimeUnit.SECONDS))
        platform.dispatchFocus(AndroidAudioFocusChange.Gain)
        assertTrue(platform.output.secondPlay.await(2, TimeUnit.SECONDS))

        session.release()
    }

    @Test
    fun `writer pauses flushes resumes handles focus and terminates once`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 8_000,
            minimumBuffers = mapOf(AndroidPcmEncoding.Float to 1_024),
        )
        val session = AndroidAudioSink(platform).openSession(MiniAppId("game.audio-test"), 2L)

        session.playMusic(compiledAndroidTone())
        assertTrue(platform.output.firstWrite.await(2, TimeUnit.SECONDS))

        platform.dispatchFocus(AndroidAudioFocusChange.Duck)
        assertTrue(platform.output.duck.await(2, TimeUnit.SECONDS))
        platform.dispatchFocus(AndroidAudioFocusChange.Gain)
        assertTrue(platform.output.gain.await(2, TimeUnit.SECONDS))

        session.updatePolicy(AudioSessionPolicy.Background)
        assertTrue(platform.output.pause.await(2, TimeUnit.SECONDS))
        assertTrue(platform.output.flush.await(2, TimeUnit.SECONDS))
        session.updatePolicy(AudioSessionPolicy.Active)
        assertTrue(platform.output.secondPlay.await(2, TimeUnit.SECONDS))

        session.release()
        session.release()

        assertTrue(platform.output.released.await(2, TimeUnit.SECONDS))
        assertEquals(1, platform.output.releaseCount)
        assertEquals(2, platform.abandonFocusCount)
        assertFalse(requireNotNull(platform.writerThread).isAlive)
    }

    @Test
    fun `platform write failure is contained in diagnostics and releases writer`() {
        val platform = RecordingAndroidAudioPlatform(
            nativeSampleRate = 8_000,
            minimumBuffers = mapOf(AndroidPcmEncoding.Float to 1_024),
        ).also { it.output.failWrites = true }
        val session = AndroidAudioSink(platform).openSession(MiniAppId("game.audio-test"), 4L)
        session.playMusic(compiledAndroidTone())

        assertTrue(platform.output.released.await(2, TimeUnit.SECONDS))

        assertEquals(1L, session.drainDiagnostics().callbackFailures)
        session.release()
        assertFalse(requireNotNull(platform.writerThread).isAlive)
    }
}

private data class MinimumBufferRequest(
    val sampleRate: Int,
    val encoding: AndroidPcmEncoding,
)

private class RecordingAndroidAudioPlatform(
    private val nativeSampleRate: Int,
    private val minimumBuffers: Map<AndroidPcmEncoding, Int>,
    private val rejectedTrackEncodings: Set<AndroidPcmEncoding> = emptySet(),
) : AndroidAudioPlatform {
    val minimumBufferRequests = mutableListOf<MinimumBufferRequest>()
    val trackCreationRequests = mutableListOf<AndroidAudioTrackConfiguration>()
    val output = RecordingAndroidAudioTrack()
    var writerThread: Thread? = null
    var abandonFocusCount = 0
    var requestFocusCount = 0
    private var focusListener: ((AndroidAudioFocusChange) -> Unit)? = null

    override fun nativeOutputSampleRate(): Int = nativeSampleRate

    override fun minimumBufferSize(sampleRate: Int, encoding: AndroidPcmEncoding): Int {
        minimumBufferRequests += MinimumBufferRequest(sampleRate, encoding)
        return minimumBuffers[encoding] ?: AndroidAudioPlatform.UnsupportedBufferSize
    }

    override fun createTrack(configuration: AndroidAudioTrackConfiguration): AndroidAudioTrack {
        trackCreationRequests += configuration
        if (configuration.encoding in rejectedTrackEncodings) error("Rejected ${configuration.encoding}")
        output.configuration = configuration
        return output
    }

    override fun requestAudioFocus(listener: (AndroidAudioFocusChange) -> Unit): Boolean {
        requestFocusCount += 1
        focusListener = listener
        return true
    }

    override fun abandonAudioFocus() {
        abandonFocusCount += 1
        focusListener = null
    }

    override fun startWriter(name: String, block: () -> Unit): Thread =
        Thread(block, name).also {
            writerThread = it
            it.start()
        }

    fun dispatchFocus(change: AndroidAudioFocusChange) {
        focusListener?.invoke(change)
    }
}

private class RecordingAndroidAudioTrack : AndroidAudioTrack {
    lateinit var configuration: AndroidAudioTrackConfiguration
    val firstWrite = CountDownLatch(1)
    val firstPlay = CountDownLatch(1)
    val nonZeroPcm16 = CountDownLatch(1)
    val duck = CountDownLatch(1)
    val gain = CountDownLatch(1)
    val pause = CountDownLatch(1)
    val flush = CountDownLatch(1)
    val secondPlay = CountDownLatch(1)
    val released = CountDownLatch(1)
    private val actions = CopyOnWriteArrayList<String>()
    var releaseCount = 0
        private set
    @Volatile var lastPcm16: ShortArray = ShortArray(0)
        private set
    @Volatile var failWrites: Boolean = false

    override fun play() {
        actions += "play"
        firstPlay.countDown()
        if (actions.count { it == "play" } == 2) secondPlay.countDown()
    }

    override fun pause() {
        check(actions.any { it == "play" }) { "pause before play" }
        actions += "pause"
        pause.countDown()
    }

    override fun flush() {
        actions += "flush"
        flush.countDown()
    }

    override fun setVolume(value: Float) {
        actions += "volume:$value"
        if (value == 0.2f) duck.countDown()
        if (value == 1f && actions.any { it == "volume:0.2" }) gain.countDown()
    }

    override fun write(samples: FloatArray, sampleCount: Int): Int {
        if (failWrites) error("write failed")
        firstWrite.countDown()
        return sampleCount
    }

    override fun write(samples: ShortArray, sampleCount: Int): Int {
        if (failWrites) error("write failed")
        lastPcm16 = samples.copyOf(sampleCount)
        if (lastPcm16.any { it != 0.toShort() }) nonZeroPcm16.countDown()
        firstWrite.countDown()
        return sampleCount
    }

    override fun release() {
        releaseCount += 1
        released.countDown()
    }
}

private fun compiledAndroidTone() = assertIs<AudioCompilationResult.Success>(
    audioProgram {
        tempo(240f)
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
        musicTrack("music") {
            instrument("tone")
            notes(MidiNote.of(69))
        }
    }.compile(),
).program
