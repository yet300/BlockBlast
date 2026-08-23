package ge.yet.game.miniapp.audio.internal

import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.AudioCommandRejection
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.CompiledAudioProgram
import ge.yet.game.miniapp.audio.MidiNote
import ge.yet.game.miniapp.audio.OscillatorShape
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.audio.audioProgram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MiniAppAudioEngineTest {
    @Test
    fun `each open returns an id and key bound facade while replacing stale handles`() = runTest {
        val setup = setup(backgroundScope)
        val first = setup.engine.openSession(ID, 1, LifecycleRegistry(), visibility())
        val second = setup.engine.openSession(ID, 2, LifecycleRegistry(), visibility())

        assertNotSame(first, second)
        assertRejected(first.playMusic(musicProgram()), AudioCommandRejection.SESSION_CLOSED)
        assertEquals(AudioCommandResult.Accepted, second.playMusic(musicProgram()))
        assertEquals(listOf(ID to 1L, ID to 2L), setup.sink.opened)
        assertEquals(1, setup.sink.sessions.first().releaseCount)
    }

    @Test
    fun `visibility ducks music suppresses obscured sfx and pauses background scheduling`() = runTest {
        val setup = setup(backgroundScope)
        val visibility = MutableVisibility()
        val audio = setup.engine.openSession(ID, 1, LifecycleRegistry(), visibility)
        runCurrent()

        val backend = setup.sink.sessions.single()
        assertEquals(AudioSessionPolicy.Active, backend.policies.last())
        assertEquals(AudioCommandResult.Accepted, audio.playSfx(sfxProgram(), SFX))

        visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        assertEquals(AudioSessionPolicy.Obscured, backend.policies.last())
        assertRejected(audio.playSfx(sfxProgram(), SFX), AudioCommandRejection.PLAYBACK_SUPPRESSED)

        visibility.set(MiniAppVisibility.BACKGROUND)
        runCurrent()
        assertEquals(AudioSessionPolicy.Background, backend.policies.last())
    }

    @Test
    fun `lifecycle destroy releases exactly once and rejects every later command`() = runTest {
        val setup = setup(backgroundScope)
        val lifecycle = LifecycleRegistry().also(LifecycleRegistry::resume)
        val audio = setup.engine.openSession(ID, 7, lifecycle, visibility())
        val backend = setup.sink.sessions.single()

        lifecycle.destroy()
        lifecycle.destroy()

        assertEquals(1, backend.releaseCount)
        assertRejected(audio.playMusic(musicProgram()), AudioCommandRejection.SESSION_CLOSED)
        assertRejected(audio.playSfx(sfxProgram(), SFX), AudioCommandRejection.SESSION_CLOSED)
        assertRejected(audio.setControl(CONTROL, 0.5f), AudioCommandRejection.SESSION_CLOSED)
    }

    @Test
    fun `music and sfx settings gate only their own buses`() = runTest {
        val settings = FakeSettingsRepository()
        val setup = setup(backgroundScope, settings)
        backgroundScope.launch { setup.engine.observeSettings() }
        val audio = setup.engine.openSession(ID, 1, LifecycleRegistry(), visibility())
        runCurrent()
        val backend = setup.sink.sessions.single()

        settings.musicEnabled.value = false
        runCurrent()
        assertEquals(0f, backend.policies.last().musicGain)
        assertTrue(backend.policies.last().acceptsNewSfx)
        assertEquals(AudioCommandResult.Accepted, audio.playMusic(musicProgram()))
        assertEquals(AudioCommandResult.Accepted, audio.playSfx(sfxProgram(), SFX))

        settings.musicEnabled.value = true
        settings.sfxEnabled.value = false
        runCurrent()
        assertEquals(1f, backend.policies.last().musicGain)
        assertTrue(!backend.policies.last().acceptsNewSfx)
        assertEquals(AudioCommandResult.Accepted, audio.playMusic(musicProgram()))
        assertRejected(audio.playSfx(sfxProgram(), SFX), AudioCommandRejection.PLAYBACK_SUPPRESSED)
    }

    @Test
    fun `backend and diagnostic failures cannot fail session creation`() = runTest {
        val crashlytics = ThrowingCrashlytics()
        val setup = setup(
            scope = backgroundScope,
            sink = RecordingSink(openFailure = IllegalStateException("backend unavailable")),
            crashlytics = crashlytics,
        )

        val audio = setup.engine.openSession(ID, 1, LifecycleRegistry(), visibility())

        assertRejected(audio.playMusic(musicProgram()), AudioCommandRejection.BACKEND_UNAVAILABLE)
        advanceUntilIdle()
    }

    @Test
    fun `repeated runtime diagnostics are drained outside the sink callback`() = runTest {
        val crashlytics = RecordingCrashlytics()
        val setup = setup(backgroundScope, crashlytics = crashlytics)
        setup.engine.openSession(ID, 1, LifecycleRegistry(), visibility())
        val backend = setup.sink.sessions.single()
        backend.diagnostics = AudioRuntimeDiagnosticsSnapshot(
            validationRejections = 1,
            queueOverflows = 2,
            forcedVoiceShedding = 3,
            callbackFailures = 1,
            underruns = 3,
        )

        setup.engine.drainDiagnostics()

        assertEquals(1, backend.drainCount)
        assertEquals(5, crashlytics.messages.size)
    }

    private fun setup(
        scope: CoroutineScope,
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        sink: RecordingSink = RecordingSink(),
        crashlytics: CrashlyticsRepository = RecordingCrashlytics(),
    ): Setup {
        val engine = DefaultMiniAppAudioEngine(scope, settings, sink, crashlytics)
        return Setup(engine, sink)
    }

    private fun visibility() = MutableVisibility()

    private fun musicProgram(): AudioProgram = audioProgram {
        control(CONTROL.value, default = 0.5f, range = 0f..1f)
        instrument("tone") { oscillator(OscillatorShape.SINE, 0.2f) }
        musicTrack("music") {
            instrument("tone")
            notes(MidiNote.of(60))
        }
    }

    private fun sfxProgram(): AudioProgram = audioProgram {
        sfx(SFX.value) { oscillator(OscillatorShape.SINE, 0.2f) }
    }

    private fun assertRejected(result: AudioCommandResult, reason: AudioCommandRejection) {
        assertEquals(reason, assertIs<AudioCommandResult.Rejected>(result).reason)
    }

    private data class Setup(
        val engine: DefaultMiniAppAudioEngine,
        val sink: RecordingSink,
    )

    private class MutableVisibility : MiniAppVisibilitySource {
        private val mutable = MutableStateFlow(MiniAppVisibility.ACTIVE)
        override val visibility: StateFlow<MiniAppVisibility> = mutable
        fun set(value: MiniAppVisibility) { mutable.value = value }
    }

    private class RecordingSink(
        private val openFailure: Throwable? = null,
    ) : PlatformAudioSink {
        val opened = mutableListOf<Pair<MiniAppId, Long>>()
        val sessions = mutableListOf<RecordingSinkSession>()

        override fun openSession(id: MiniAppId, sessionKey: Long): PlatformAudioSinkSession {
            openFailure?.let { throw it }
            opened += id to sessionKey
            return RecordingSinkSession().also(sessions::add)
        }
    }

    private class RecordingSinkSession : PlatformAudioSinkSession {
        val policies = mutableListOf<AudioSessionPolicy>()
        var releaseCount = 0
        var drainCount = 0
        var diagnostics = AudioRuntimeDiagnosticsSnapshot.Empty

        override fun updatePolicy(policy: AudioSessionPolicy) { policies += policy }
        override fun playMusic(program: CompiledAudioProgram) = AudioRuntimeSubmitResult.Accepted
        override fun stopMusic(fadeFrames: Int) = AudioRuntimeSubmitResult.Accepted
        override fun playSfx(program: CompiledAudioProgram, name: SfxName) = AudioRuntimeSubmitResult.Accepted
        override fun setControl(name: AudioControlName, value: Float) = AudioRuntimeSubmitResult.Accepted
        override fun release() { releaseCount += 1 }
        override fun drainDiagnostics(): AudioRuntimeDiagnosticsSnapshot {
            drainCount += 1
            return diagnostics.also { diagnostics = AudioRuntimeDiagnosticsSnapshot.Empty }
        }
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val musicEnabled = MutableStateFlow(true)
        override val sfxEnabled = MutableStateFlow(true)
        override val vibrationEnabled = MutableStateFlow(true)
        override val darkTheme = MutableStateFlow(false)
        override val adsEnabled = MutableStateFlow(true)
        override suspend fun setMusicEnabled(enabled: Boolean) { musicEnabled.value = enabled }
        override suspend fun setSfxEnabled(enabled: Boolean) { sfxEnabled.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) { vibrationEnabled.value = enabled }
        override suspend fun setDarkTheme(enabled: Boolean) { darkTheme.value = enabled }
        override suspend fun setAdsEnabled(enabled: Boolean) { adsEnabled.value = enabled }
    }

    private open class RecordingCrashlytics : CrashlyticsRepository {
        val messages = mutableListOf<String>()
        override fun setUserID(id: String) = Unit
        override fun clearUserID() = Unit
        override fun setCustomValue(key: String, value: Any) = Unit
        override fun logException(throwable: Throwable) { messages += "exception:${throwable.message}" }
        override fun logMessage(message: String) { messages += message }
    }

    private class ThrowingCrashlytics : RecordingCrashlytics() {
        override fun logException(throwable: Throwable) = error("diagnostics unavailable")
        override fun logMessage(message: String) = error("diagnostics unavailable")
    }

    private companion object {
        val ID = MiniAppId("game.audio_test")
        val SFX = SfxName("click")
        val CONTROL = AudioControlName("intensity")
    }
}
