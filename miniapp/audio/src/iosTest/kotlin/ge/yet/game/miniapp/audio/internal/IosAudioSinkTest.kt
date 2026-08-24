package ge.yet.game.miniapp.audio.internal

import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.audio.AudioCompilationResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import ge.yet.game.miniapp.audio.compile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback

class IosAudioSinkTest {
    @Test
    fun `default platform configures playback that survives silent mode`() {
        DefaultIosAudioPlatform().configureSession()

        assertEquals(AVAudioSessionCategoryPlayback, AVAudioSession.sharedInstance().category)
    }

    @Test
    fun `opening configures one observed engine without activating playback`() {
        val platform = RecordingIosAudioPlatform()

        val session = IosAudioSink(platform, ::RecordingIosAudioRenderer)
            .openSession(MiniAppId("game.audio-test"), 1L)

        assertEquals(48_000, session.sampleRate)
        assertEquals(1, platform.configureCount)
        assertEquals(1, platform.engines.size)
        assertEquals(1, platform.observeCount)
        assertEquals(emptyList(), platform.sessionActivity)
        assertEquals(0, platform.engines.single().startCount)

        session.release()
    }

    @Test
    fun `play prefills producer before engine start and callback only drains produced PCM`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events)
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 2L)
        platform.events.clear()

        assertEquals(AudioRuntimeSubmitResult.Accepted, session.playMusic(compiledIosTone()))

        val rendered = platform.engines.single().render(64)
        assertEquals(
            listOf("producer:submit", "session:true", "producer:resume", "engine:start"),
            platform.events,
        )
        assertEquals(listOf(true), platform.sessionActivity)
        assertEquals(1, platform.engines.single().startCount)
        assertTrue(rendered.first.any { it == 0.25f })
        assertTrue(rendered.second.any { it == -0.25f })

        session.release()
    }

    @Test
    fun `failed prefill does not start engine and deactivates session`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events).also { it.prefillSucceeds = false }
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        ).openSession(MiniAppId("game.audio-test"), 20L)
        platform.events.clear()

        session.playMusic(compiledIosTone())

        assertEquals(
            listOf("producer:submit", "session:true", "producer:resume", "producer:pause", "session:false"),
            platform.events,
        )
        assertEquals(0, platform.engines.single().startCount)
        session.release()
    }

    @Test
    fun `new transition during prefill invalidates stale engine start`() {
        val platform = RecordingIosAudioPlatform()
        lateinit var session: PlatformAudioSinkSession
        val producer = RecordingIosPcmProducer(platform.events).also {
            it.onResume = { session.updatePolicy(AudioSessionPolicy.Background) }
        }
        session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        ).openSession(MiniAppId("game.audio-test"), 21L)
        platform.events.clear()

        session.playMusic(compiledIosTone())

        assertEquals(0, platform.engines.single().startCount)
        assertTrue("producer:policy" in platform.events)
        assertTrue("producer:pause" in platform.events)
        assertEquals(false, platform.sessionActivity.last())
        session.release()
    }

    @Test
    fun `background and interruption pause while foreground and resumable end restart`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events)
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 3L)
        val engine = platform.engines.single()
        session.playMusic(compiledIosTone())

        platform.events.clear()
        session.updatePolicy(AudioSessionPolicy.Background)
        assertEquals(
            listOf("producer:policy", "engine:pause", "producer:pause", "session:false"),
            platform.events,
        )
        assertEquals(1, engine.pauseCount)
        assertEquals(listOf(true, false), platform.sessionActivity)

        platform.events.clear()
        session.updatePolicy(AudioSessionPolicy.Active)
        assertEquals(
            listOf("producer:policy", "session:true", "producer:resume", "engine:start"),
            platform.events,
        )
        assertEquals(2, engine.startCount)
        platform.events.clear()
        platform.dispatch(IosAudioEvent.InterruptionBegan)
        assertEquals(listOf("engine:pause", "producer:pause", "session:false"), platform.events)
        assertEquals(2, engine.pauseCount)
        platform.events.clear()
        platform.dispatch(IosAudioEvent.InterruptionEnded(shouldResume = true))
        assertEquals(listOf("session:true", "producer:resume", "engine:start"), platform.events)
        assertEquals(3, engine.startCount)

        session.release()
    }

    @Test
    fun `non-resumable interruption waits for a new explicit play request`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events)
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 7L)
        val engine = platform.engines.single()
        session.playMusic(compiledIosTone())

        platform.dispatch(IosAudioEvent.InterruptionBegan)
        platform.dispatch(IosAudioEvent.InterruptionEnded(shouldResume = false))
        assertEquals(1, engine.startCount)

        session.playMusic(compiledIosTone())
        assertEquals(2, engine.startCount)

        session.release()
    }

    @Test
    fun `route change resets engine and media reset rebuilds it`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events)
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 4L)
        session.playMusic(compiledIosTone())
        val first = platform.engines.single()

        platform.events.clear()
        platform.dispatch(IosAudioEvent.RouteChanged)
        assertEquals(
            listOf(
                "engine:pause", "producer:pause", "engine:reset",
                "producer:resume", "engine:start",
            ),
            platform.events,
        )
        assertEquals(1, first.resetCount)
        assertEquals(2, first.startCount)

        platform.events.clear()
        platform.dispatch(IosAudioEvent.MediaServicesReset)
        assertEquals(
            listOf(
                "engine:pause", "producer:pause", "engine:stop", "engine:release", "session:false",
                "session:true", "producer:resume", "engine:start",
            ),
            platform.events,
        )
        assertFalse("producer:terminate" in platform.events)
        assertEquals(2, platform.configureCount)
        assertEquals(2, platform.engines.size)
        assertEquals(1, first.releaseCount)
        assertEquals(1, platform.engines.last().startCount)

        session.release()
    }

    @Test
    fun `producer render failure is drained as a callback diagnostic`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events).also { it.renderFailures = 1 }
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 6L)
        session.playMusic(compiledIosTone())

        assertEquals(1L, session.drainDiagnostics().callbackFailures)
        session.release()
    }

    @Test
    fun `release is idempotent removes observations and rejects later commands`() {
        val platform = RecordingIosAudioPlatform()
        val producer = RecordingIosPcmProducer(platform.events)
        val session = IosAudioSink(
            platform = platform,
            producerFactory = IosPcmProducerFactory { _, _, _ -> producer },
        )
            .openSession(MiniAppId("game.audio-test"), 5L)
        session.playMusic(compiledIosTone())
        val engine = platform.engines.single()

        platform.events.clear()
        session.release()
        session.release()

        assertEquals(
            listOf(
                "engine:pause", "engine:stop", "producer:terminate", "engine:release",
                "observation:remove", "session:false",
            ),
            platform.events,
        )

        assertEquals(1, engine.stopCount)
        assertEquals(1, engine.releaseCount)
        assertEquals(1, platform.removeObservationCount)
        assertFalse(platform.observing)
        assertEquals(AudioRuntimeSubmitResult.RejectedDestroyed, session.playMusic(compiledIosTone()))
    }
}

private class RecordingIosAudioPlatform : IosAudioPlatform {
    override val sampleRate: Int = 48_000
    override val maximumFramesPerSlice: Int = 256
    val engines = mutableListOf<RecordingIosAudioEngine>()
    val sessionActivity = mutableListOf<Boolean>()
    val events = mutableListOf<String>()
    var configureCount = 0
    var observeCount = 0
    var removeObservationCount = 0
    var observing = false
    private var listener: ((IosAudioEvent) -> Unit)? = null

    override fun configureSession() {
        configureCount += 1
    }

    override fun setSessionActive(active: Boolean) {
        sessionActivity += active
        events += "session:$active"
    }

    override fun createEngine(sampleRate: Int, renderer: IosPcmBlockRenderer): IosAudioEngine =
        RecordingIosAudioEngine(renderer, events).also(engines::add)

    override fun observeEvents(listener: (IosAudioEvent) -> Unit): IosAudioObservation {
        observeCount += 1
        observing = true
        this.listener = listener
        return IosAudioObservation {
            if (observing) {
                observing = false
                removeObservationCount += 1
                events += "observation:remove"
                this.listener = null
            }
        }
    }

    fun dispatch(event: IosAudioEvent) {
        listener?.invoke(event)
    }
}

private class RecordingIosAudioEngine(
    private val renderer: IosPcmBlockRenderer,
    private val events: MutableList<String>,
) : IosAudioEngine {
    var startCount = 0
    var pauseCount = 0
    var resetCount = 0
    var stopCount = 0
    var releaseCount = 0

    override fun start() { startCount += 1; events += "engine:start" }
    override fun pause() { pauseCount += 1; events += "engine:pause" }
    override fun reset() { resetCount += 1; events += "engine:reset" }
    override fun stop() { stopCount += 1; events += "engine:stop" }
    override fun release() { releaseCount += 1; events += "engine:release" }

    fun render(frameCount: Int): Pair<FloatArray, FloatArray> {
        val left = FloatArray(frameCount)
        val right = FloatArray(frameCount)
        renderer.render(left, right, frameCount)
        return left to right
    }
}

private class RecordingIosPcmProducer(
    private val events: MutableList<String>,
) : IosPcmProducerSession {
    private val ring = StereoPcmRingBuffer(256)
    override val callbackSource = IosPcmCallbackSource(ring)
    var prefillSucceeds = true
    var renderFailures = 0L
    var onResume: (() -> Unit)? = null
    private var terminated = false

    override fun submit(command: AudioCommand): AudioRuntimeSubmitResult {
        events += "producer:submit"
        return if (terminated) AudioRuntimeSubmitResult.RejectedDestroyed else AudioRuntimeSubmitResult.Accepted
    }

    override fun updatePolicy(policy: AudioSessionPolicy) {
        events += "producer:policy"
    }

    override fun resumeAndAwaitPrefill(): Boolean {
        events += "producer:resume"
        onResume?.invoke()
        if (!prefillSucceeds) return false
        val left = FloatArray(256) { 0.25f }
        val right = FloatArray(256) { -0.25f }
        ring.write(left, right, left.size)
        return true
    }

    override fun pauseAndReset() {
        events += "producer:pause"
        ring.reset()
    }

    override fun terminate() {
        if (terminated) return
        terminated = true
        events += "producer:terminate"
    }

    override fun drainRuntimeDiagnostics(): AudioRuntimeDiagnosticsSnapshot = AudioRuntimeDiagnosticsSnapshot.Empty

    override fun drainProducerDiagnostics(): IosPcmProducerDiagnostics =
        IosPcmProducerDiagnostics(renderFailures = renderFailures).also { renderFailures = 0 }
}

private class RecordingIosAudioRenderer(
    sampleRate: Int,
    frameCapacity: Int,
) : IosAudioRenderer {
    var playMusicCount = 0
    var failRender = false

    override fun updatePolicy(policy: AudioSessionPolicy) = Unit

    override fun render(left: FloatArray, right: FloatArray, frameCount: Int) {
        if (failRender) error("render failed")
        left.fill(0.25f, 0, frameCount)
        right.fill(-0.25f, 0, frameCount)
    }

    override fun playMusic(program: CompiledAudioProgram): AudioRuntimeCommandOutcome {
        playMusicCount += 1
        return AudioRuntimeCommandOutcome.APPLIED
    }

    override fun stopMusic(fadeFrames: Int): AudioRuntimeCommandOutcome = AudioRuntimeCommandOutcome.APPLIED

    override fun playSfx(program: CompiledAudioProgram, name: SfxName): AudioRuntimeCommandOutcome =
        AudioRuntimeCommandOutcome.APPLIED

    override fun setControl(name: AudioControlName, value: Float): AudioRuntimeCommandOutcome =
        AudioRuntimeCommandOutcome.APPLIED

    override fun destroy(): AudioRuntimeCommandOutcome = AudioRuntimeCommandOutcome.APPLIED
}

private fun compiledIosTone() = assertIs<AudioCompilationResult.Success>(
    audioProgram {
        tempo(240f)
        instrument("tone") { oscillator(OscillatorShape.SINE, gain = 0.4f) }
        musicTrack("music") {
            instrument("tone")
            notes(MidiNote.of(69))
        }
    }.compile(),
).program
