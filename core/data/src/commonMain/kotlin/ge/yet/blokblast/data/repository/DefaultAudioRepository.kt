package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Guards every SFX call with the live `soundEnabled` flag, then delegates to the
 * platform bridge. Observes settings reactively so toggling sound off stops music
 * immediately, and toggling back on restarts it if a game is active.
 *
 * `internal` — hidden from composeApp/features; only the [AudioRepository]
 * interface is visible through the DI graph.
 */
@SingleIn(AppScope::class)
@Inject
internal class DefaultAudioRepository(
    private val player: PlatformSoundPlayer,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope,
) : AudioRepository {

    /** True while a game session has called [startMusic] and not yet called [stopMusic]. */
    private var musicRequested = false

    init {
        // React to sound toggle changes:
        //   sound off → stop music immediately
        //   sound on  → restart music if a game session is active
        scope.launch {
            settings.soundEnabled.collect { enabled ->
                if (!enabled) {
                    player.stopMusic()
                } else if (musicRequested) {
                    player.startMusic()
                }
            }
        }
    }

    private inline fun ifEnabled(block: () -> Unit) {
        if (settings.soundEnabled.value) block()
    }

    override suspend fun playPlacementSound() = ifEnabled { player.playPlacement() }

    override suspend fun playClearSound(lines: Int) = ifEnabled { player.playClear(lines) }

    override suspend fun playVoiceFeedback(type: FeedbackType) =
        ifEnabled { player.playVoiceFeedback(type) }

    override suspend fun playVoiceCombo(combo: Int) =
        ifEnabled { player.playVoiceCombo(combo) }

    override suspend fun startMusic() {
        musicRequested = true
        ifEnabled { player.startMusic() }
    }

    override suspend fun stopMusic() {
        musicRequested = false
        player.stopMusic() // always stop regardless of setting
    }

    override suspend fun onAppBackground() {
        // Stop audio immediately; keep musicRequested so foreground can resume.
        player.stopMusic()
    }

    override suspend fun onAppForeground() {
        // Resume music only if a game session had it running and sound is on.
        if (musicRequested) ifEnabled { player.startMusic() }
    }
}
