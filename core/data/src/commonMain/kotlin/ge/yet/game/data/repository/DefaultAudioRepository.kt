package ge.yet.game.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.data.platform.PlatformSoundPlayer
import ge.yet.game.domain.repository.AudioRepository
import ge.yet.game.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Guards every SFX/voice call with [SettingsRepository.sfxEnabled] and gates
 * music separately on [SettingsRepository.musicEnabled].
 *
 * Music lifecycle is driven by:
 *   - [requestedTracks]: a flow populated by [startMusic] (game session active)
 *     and cleared by [stopMusic] (round ended or component destroyed).
 *   - [appForeground]: a flow set false on [onAppBackground] and true on
 *     [onAppForeground]. Backgrounding the app silences music without
 *     forgetting that a session is active.
 *   - [SettingsRepository.musicEnabled]: user preference (music-only).
 *
 * Music plays iff *all three* are true. A single coroutine collects the
 * combined state and serializes start/stop calls to the platform player.
 * Lifecycle commands update [appForeground] synchronously, so a rapid bg→fg
 * cycle cannot be reordered by separate fire-and-forget coroutines.
 *
 * `internal` — only the [AudioRepository] interface is exposed via DI.
 */
@SingleIn(AppScope::class)
@Inject
internal class DefaultAudioRepository(
    private val player: PlatformSoundPlayer,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : AudioRepository {

    /** Non-empty while a game session has requested a playlist. */
    private val requestedTracks = MutableStateFlow<List<String>>(emptyList())

    /** True while the app is in the foreground (UI visible). */
    private val appForeground = MutableStateFlow(true)

    init {
        // Single source of truth: combine all three signals; whenever they
        // align to true, ask the platform player to start; on any false,
        // stop. Because this is one coroutine, calls to the player are
        // serialized — no two starts or stops can interleave.
        scope.launch {
            combine(
                requestedTracks,
                appForeground,
                settings.musicEnabled,
            ) { tracks, foreground, enabled ->
                if (foreground && enabled) tracks else emptyList()
            }
                // distinctUntilChanged is critical: combine() re-emits whenever
                // any upstream emits, even if the boolean output didn't change.
                // Without this, a transient state-flip would call
                // player.startMusic / stopMusic in rapid sequence and tear
                // down a still-preparing MediaPlayer.
                .distinctUntilChanged()
                .collect { tracks ->
                    if (tracks.isNotEmpty()) player.startMusic(tracks) else player.stopMusic()
                }
        }
    }

    private inline fun ifSfxEnabled(block: () -> Unit) {
        if (settings.sfxEnabled.value) block()
    }

    override fun playSound(filename: String) =
        ifSfxEnabled { player.playSound(filename) }

    override fun startMusic(tracks: List<String>) {
        requestedTracks.value = tracks.toList()
    }

    override fun stopMusic() {
        requestedTracks.value = emptyList()
    }

    override fun onAppBackground() {
        appForeground.value = false
    }

    override fun onAppForeground() {
        appForeground.value = true
    }
}
