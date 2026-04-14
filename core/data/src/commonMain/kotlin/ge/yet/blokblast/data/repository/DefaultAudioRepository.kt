package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository


/**
 * Guards every call with the live `soundEnabled` flag, then delegates to the
 * platform bridge. Completely replaces the old no-op stub.
 *
 * `internal` — hidden from composeApp/features; only the [AudioRepository]
 * interface is visible through the DI graph.
 */
@SingleIn(AppScope::class)
@Inject
internal class DefaultAudioRepository(
    private val player: PlatformSoundPlayer,
    private val settings: SettingsRepository,
) : AudioRepository {

    private inline fun ifEnabled(block: () -> Unit) {
        if (settings.soundEnabled.value) block()
    }

    override suspend fun playPlacementSound() = ifEnabled { player.playPlacement() }

    override suspend fun playClearSound(lines: Int) = ifEnabled { player.playClear(lines) }

    override suspend fun playVoiceFeedback(type: FeedbackType) =
        ifEnabled { player.playVoiceFeedback(type) }

    override suspend fun playVoiceCombo(combo: Int) =
        ifEnabled { player.playVoiceCombo(combo) }
}
