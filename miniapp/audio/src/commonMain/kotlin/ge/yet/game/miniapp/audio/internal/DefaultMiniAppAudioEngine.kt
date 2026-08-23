package ge.yet.game.miniapp.audio.internal

import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import ge.yet.game.domain.repository.CrashlyticsRepository
import ge.yet.game.domain.repository.SettingsRepository
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.api.MiniAppVisibilitySource
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.MiniAppAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

internal class DefaultMiniAppAudioEngine(
    private val appScope: CoroutineScope,
    private val settings: SettingsRepository,
    private val sink: PlatformAudioSink,
    crashlytics: CrashlyticsRepository,
) : MiniAppAudioEngine {
    private val diagnostics = AudioDiagnostics(crashlytics)
    private val active = MutableStateFlow<ActiveAudioSession?>(null)

    override fun openSession(
        id: MiniAppId,
        sessionKey: Long,
        lifecycle: Lifecycle,
        visibility: MiniAppVisibilitySource,
    ): MiniAppAudio {
        active.value?.audio?.close()
        val backend = try {
            sink.openSession(id, sessionKey)
        } catch (error: Exception) {
            diagnostics.backendFailure(error)
            null
        }
        lateinit var audio: DefaultMiniAppAudio
        audio = DefaultMiniAppAudio(
            backend = backend,
            diagnostics = diagnostics,
            initialVisibility = visibility.visibility.value,
            initialMusicEnabled = settings.musicEnabled.value,
            initialSfxEnabled = settings.sfxEnabled.value,
            onClosed = { clearIfActive(id, sessionKey, audio) },
        )
        active.value = ActiveAudioSession(id, sessionKey, audio)
        audio.attachVisibilityJob(
            appScope.launch {
                visibility.visibility.collect(audio::updateVisibility)
            },
        )
        lifecycle.doOnDestroy { closeSession(id, sessionKey) }
        return audio
    }

    override fun closeSession(id: MiniAppId, sessionKey: Long) {
        val session = active.value?.takeIf { it.id == id && it.sessionKey == sessionKey } ?: return
        session.audio.close()
    }

    suspend fun observeSettings() {
        combine(settings.musicEnabled, settings.sfxEnabled, ::Pair).collect { (music, sfx) ->
            active.value?.audio?.updateSettings(music, sfx)
        }
    }

    suspend fun reportDiagnostics(periodMillis: Long = DIAGNOSTIC_PERIOD_MILLIS) {
        require(periodMillis > 0)
        while (coroutineContext.isActive) {
            delay(periodMillis)
            drainDiagnostics()
        }
    }

    fun drainDiagnostics() {
        active.value?.audio?.drainDiagnostics()
    }

    private fun clearIfActive(id: MiniAppId, sessionKey: Long, audio: DefaultMiniAppAudio) {
        active.compareAndSet(ActiveAudioSession(id, sessionKey, audio), null)
    }

    private data class ActiveAudioSession(
        val id: MiniAppId,
        val sessionKey: Long,
        val audio: DefaultMiniAppAudio,
    )
}

private const val DIAGNOSTIC_PERIOD_MILLIS = 15_000L
