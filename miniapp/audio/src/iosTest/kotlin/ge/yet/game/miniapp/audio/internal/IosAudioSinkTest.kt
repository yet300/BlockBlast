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
    fun `play starts engine and callback consumes commands through injected renderer`() {
        val platform = RecordingIosAudioPlatform()
        val renderer = RecordingIosAudioRenderer(platform.sampleRate, platform.maximumFramesPerSlice)
        val session = IosAudioSink(platform) { _, _ -> renderer }
            .openSession(MiniAppId("game.audio-test"), 2L)

        assertEquals(AudioRuntimeSubmitResult.Accepted, session.playMusic(compiledIosTone()))

        val rendered = platform.engines.single().render(64)
        assertEquals(listOf(true), platform.sessionActivity)
        assertEquals(1, platform.engines.single().startCount)
        assertEquals(1, renderer.playMusicCount)
        assertTrue(rendered.first.any { it == 0.25f })
        assertTrue(rendered.second.any { it == -0.25f })

        session.release()
    }

    @Test
    fun `background and interruption pause while foreground and resumable end restart`() {
        val platform = RecordingIosAudioPlatform()
        val session = IosAudioSink(platform, ::RecordingIosAudioRenderer)
            .openSession(MiniAppId("game.audio-test"), 3L)
        val engine = platform.engines.single()
        session.playMusic(compiledIosTone())

        session.updatePolicy(AudioSessionPolicy.Background)
        assertEquals(1, engine.pauseCount)
        assertEquals(listOf(true, false), platform.sessionActivity)

        session.updatePolicy(AudioSessionPolicy.Active)
        assertEquals(2, engine.startCount)
        platform.dispatch(IosAudioEvent.InterruptionBegan)
        assertEquals(2, engine.pauseCount)
        platform.dispatch(IosAudioEvent.InterruptionEnded(shouldResume = true))
        assertEquals(3, engine.startCount)

        session.release()
    }

    @Test
    fun `non-resumable interruption waits for a new explicit play request`() {
        val platform = RecordingIosAudioPlatform()
        val session = IosAudioSink(platform, ::RecordingIosAudioRenderer)
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
        val session = IosAudioSink(platform, ::RecordingIosAudioRenderer)
            .openSession(MiniAppId("game.audio-test"), 4L)
        session.playMusic(compiledIosTone())
        val first = platform.engines.single()

        platform.dispatch(IosAudioEvent.RouteChanged)
        assertEquals(1, first.resetCount)
        assertEquals(2, first.startCount)

        platform.dispatch(IosAudioEvent.MediaServicesReset)
        assertEquals(2, platform.configureCount)
        assertEquals(2, platform.engines.size)
        assertEquals(1, first.releaseCount)
        assertEquals(1, platform.engines.last().startCount)

        session.release()
    }

    @Test
    fun `renderer failure cannot escape callback and is drained as a diagnostic`() {
        val platform = RecordingIosAudioPlatform()
        val renderer = RecordingIosAudioRenderer(platform.sampleRate, platform.maximumFramesPerSlice).also {
            it.failRender = true
        }
        val session = IosAudioSink(platform) { _, _ -> renderer }
            .openSession(MiniAppId("game.audio-test"), 6L)
        session.playMusic(compiledIosTone())

        val rendered = platform.engines.single().render(64)

        assertTrue(rendered.first.all { it == 0f })
        assertTrue(rendered.second.all { it == 0f })
        assertEquals(1L, session.drainDiagnostics().callbackFailures)
        session.release()
    }

    @Test
    fun `release is idempotent removes observations and rejects later commands`() {
        val platform = RecordingIosAudioPlatform()
        val session = IosAudioSink(platform, ::RecordingIosAudioRenderer)
            .openSession(MiniAppId("game.audio-test"), 5L)
        session.playMusic(compiledIosTone())
        val engine = platform.engines.single()

        session.release()
        session.release()

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
    }

    override fun createEngine(sampleRate: Int, renderer: IosPcmBlockRenderer): IosAudioEngine =
        RecordingIosAudioEngine(renderer).also(engines::add)

    override fun observeEvents(listener: (IosAudioEvent) -> Unit): IosAudioObservation {
        observeCount += 1
        observing = true
        this.listener = listener
        return IosAudioObservation {
            if (observing) {
                observing = false
                removeObservationCount += 1
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
) : IosAudioEngine {
    var startCount = 0
    var pauseCount = 0
    var resetCount = 0
    var stopCount = 0
    var releaseCount = 0

    override fun start() { startCount += 1 }
    override fun pause() { pauseCount += 1 }
    override fun reset() { resetCount += 1 }
    override fun stop() { stopCount += 1 }
    override fun release() { releaseCount += 1 }

    fun render(frameCount: Int): Pair<FloatArray, FloatArray> {
        val left = FloatArray(frameCount)
        val right = FloatArray(frameCount)
        renderer.render(left, right, frameCount)
        return left to right
    }
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
