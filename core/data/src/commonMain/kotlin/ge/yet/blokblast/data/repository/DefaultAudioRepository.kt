package ge.yet.blokblast.data.repository

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.AudioRepository
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Guards every SFX call with the live `soundEnabled` flag, then delegates to the
 * platform bridge.
 *
 * Music lifecycle is driven by:
 *   - [musicRequested]: a flow set true by [startMusic] (game session active)
 *     and false by [stopMusic] (round ended or component destroyed).
 *   - [appForeground]: a flow set false on [onAppBackground] and true on
 *     [onAppForeground]. Backgrounding the app silences music without
 *     forgetting that a session is active.
 *   - [SettingsRepository.soundEnabled]: user preference.
 *
 * Music plays iff *all three* are true. A single coroutine collects the
 * combine of those flows and serializes start/stop calls to the platform
 * player — that prevents the start/stop ping-pong race that previously
 * occurred when the OS rapidly toggled foreground/background (fingerprint
 * prompt, ad close) and the two `scope.launch { ... }`-ed lifecycle handlers
 * raced.
 *
 * Lifecycle hooks now post events to a [Channel] consumed by that single
 * coroutine, so the order of fg/bg events is preserved regardless of how
 * many coroutines fire simultaneously.
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

    /** True while a game session has called [startMusic] and not yet [stopMusic]. */
    private val musicRequested = MutableStateFlow(false)

    /** True while the app is in the foreground (UI visible). */
    private val appForeground = MutableStateFlow(true)

    /**
     * Serialized lifecycle event stream — both foreground and background
     * transitions go through here so a rapid bg→fg cycle can never reorder.
     * Capacity = 1 with overflow = DROP_OLDEST: only the most recent state
     * matters for the player.
     */
    private val lifecycleEvents = Channel<Boolean>(capacity = Channel.CONFLATED)

    init {
        // Single source of truth: combine all three signals; whenever they
        // align to true, ask the platform player to start; on any false,
        // stop. Because this is one coroutine, calls to the player are
        // serialized — no two starts or stops can interleave.
        scope.launch {
            combine(
                musicRequested,
                appForeground,
                settings.soundEnabled,
            ) { requested, foreground, enabled -> requested && foreground && enabled }
                // distinctUntilChanged is critical: combine() re-emits whenever
                // any upstream emits, even if the boolean output didn't change.
                // Without this, a transient state-flip would call
                // player.startMusic / stopMusic in rapid sequence and tear
                // down a still-preparing MediaPlayer.
                .distinctUntilChanged()
                .collect { shouldPlay ->
                    if (shouldPlay) player.startMusic() else player.stopMusic()
                }
        }
        // Drain lifecycle events into appForeground in the order received.
        scope.launch {
            for (foreground in lifecycleEvents) {
                appForeground.value = foreground
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
        musicRequested.value = true
    }

    override suspend fun stopMusic() {
        musicRequested.value = false
    }

    override suspend fun onAppBackground() {
        // Send through the channel so this is ordered with onAppForeground.
        lifecycleEvents.trySend(false)
    }

    override suspend fun onAppForeground() {
        lifecycleEvents.trySend(true)
    }
}
