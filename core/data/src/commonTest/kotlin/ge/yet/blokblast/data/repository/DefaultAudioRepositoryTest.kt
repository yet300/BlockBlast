package ge.yet.blokblast.data.repository

import ge.yet.blokblast.data.platform.PlatformSoundPlayer
import ge.yet.blokblast.domain.model.FeedbackType
import ge.yet.blokblast.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultAudioRepositoryTest {

    /**
     * The repo's init-time combine collector emits its initial value (a Stop,
     * since musicRequested starts at false). Tests care about post-init
     * transitions, so we snapshot and discard that initial emission.
     */
    private fun setup(
        music: Boolean = true,
        sfx: Boolean = true,
    ): Triple<DefaultAudioRepository, RecordingPlayer, FakeSettings> {
        val player = RecordingPlayer()
        val settings = FakeSettings(musicEnabled = music, sfxEnabled = sfx)
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val repo = DefaultAudioRepository(player, settings, scope)
        player.calls.clear()
        return Triple(repo, player, settings)
    }

    // ── Music gating ─────────────────────────────────────────────────────

    @Test
    fun startMusic_starts_when_fg_and_sound_on() = runTest {
        val (repo, player) = setup()
        repo.startMusic()
        assertEquals(listOf(PlayerCall.Start), player.calls)
    }

    @Test
    fun startMusic_does_not_start_when_music_off() = runTest {
        val (repo, player) = setup(music = false)
        repo.startMusic()
        assertTrue(player.calls.isEmpty())
    }

    @Test
    fun startMusic_starts_when_music_on_even_if_sfx_off() = runTest {
        val (repo, player) = setup(music = true, sfx = false)
        repo.startMusic()
        assertEquals(listOf(PlayerCall.Start), player.calls)
    }

    @Test
    fun stopMusic_stops_active_music() = runTest {
        val (repo, player) = setup()
        repo.startMusic()
        repo.stopMusic()
        assertEquals(listOf(PlayerCall.Start, PlayerCall.Stop), player.calls)
    }

    @Test
    fun repeat_startMusic_does_not_double_start() = runTest {
        val (repo, player) = setup()
        repo.startMusic()
        repo.startMusic()
        repo.startMusic()
        assertEquals(listOf(PlayerCall.Start), player.calls)
    }

    @Test
    fun background_then_foreground_pauses_and_resumes() = runTest {
        val (repo, player) = setup()
        repo.startMusic()
        repo.onAppBackground()
        repo.onAppForeground()
        assertEquals(
            listOf(PlayerCall.Start, PlayerCall.Stop, PlayerCall.Start),
            player.calls,
        )
    }

    @Test
    fun toggling_music_off_then_on_during_music_stops_then_starts() = runTest {
        val (repo, player, settings) = setup()
        repo.startMusic()
        settings.musicFlow.value = false
        settings.musicFlow.value = true
        assertEquals(
            listOf(PlayerCall.Start, PlayerCall.Stop, PlayerCall.Start),
            player.calls,
        )
    }

    @Test
    fun toggling_sfx_does_not_affect_music_playback() = runTest {
        val (repo, player, settings) = setup()
        repo.startMusic()
        val baseline = player.calls.toList()
        settings.sfxFlow.value = false
        settings.sfxFlow.value = true
        assertEquals(baseline, player.calls)
    }

    @Test
    fun stopMusic_while_backgrounded_does_not_emit_extra_stop() = runTest {
        val (repo, player) = setup()
        repo.startMusic()
        repo.onAppBackground()
        val mid = player.calls.size
        repo.stopMusic()
        // Already stopped; combined boolean still false → no new emission.
        assertEquals(mid, player.calls.size)
    }

    // ── SFX gating ───────────────────────────────────────────────────────

    @Test
    fun sfx_play_when_enabled() = runTest {
        val (repo, player) = setup()
        repo.playPlacementSound()
        repo.playClearSound(2)
        repo.playVoiceFeedback(FeedbackType.GOOD)
        repo.playVoiceCombo(3)
        assertTrue(player.placement)
        assertEquals(listOf(2), player.clears)
        assertEquals(listOf(FeedbackType.GOOD), player.voiceFeedback)
        assertEquals(listOf(3), player.voiceCombo)
    }

    @Test
    fun sfx_silent_when_disabled() = runTest {
        val (repo, player) = setup(sfx = false)
        repo.playPlacementSound()
        repo.playClearSound(2)
        repo.playVoiceFeedback(FeedbackType.GOOD)
        repo.playVoiceCombo(3)
        assertEquals(false, player.placement)
        assertTrue(player.clears.isEmpty())
        assertTrue(player.voiceFeedback.isEmpty())
        assertTrue(player.voiceCombo.isEmpty())
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private enum class PlayerCall { Start, Stop }

    private class RecordingPlayer : PlatformSoundPlayer {
        val calls = mutableListOf<PlayerCall>()
        var placement = false
        val clears = mutableListOf<Int>()
        val voiceFeedback = mutableListOf<FeedbackType>()
        val voiceCombo = mutableListOf<Int>()
        override fun playPlacement() { placement = true }
        override fun playClear(lines: Int) { clears += lines }
        override fun playVoiceFeedback(type: FeedbackType) { voiceFeedback += type }
        override fun playVoiceCombo(combo: Int) { voiceCombo += combo }
        override fun startMusic() { calls += PlayerCall.Start }
        override fun stopMusic() { calls += PlayerCall.Stop }
        override fun release() {}
    }

    private class FakeSettings(
        musicEnabled: Boolean = true,
        sfxEnabled: Boolean = true,
    ) : SettingsRepository {
        val musicFlow = MutableStateFlow(musicEnabled)
        val sfxFlow = MutableStateFlow(sfxEnabled)
        override val musicEnabled: StateFlow<Boolean> = musicFlow.asStateFlow()
        override val sfxEnabled: StateFlow<Boolean> = sfxFlow.asStateFlow()
        override val vibrationEnabled = MutableStateFlow(true).asStateFlow()
        override val darkTheme = MutableStateFlow(false).asStateFlow()
        override val bestScore = MutableStateFlow(0L).asStateFlow()
        override val reviewPromptCount = MutableStateFlow(0).asStateFlow()
        override val tutorialSeen = MutableStateFlow(false).asStateFlow()
        override suspend fun setMusicEnabled(enabled: Boolean) { musicFlow.value = enabled }
        override suspend fun setSfxEnabled(enabled: Boolean) { sfxFlow.value = enabled }
        override suspend fun setVibrationEnabled(enabled: Boolean) {}
        override suspend fun setDarkTheme(enabled: Boolean) {}
        override suspend fun setBestScore(score: Long) {}
        override suspend fun incrementReviewPromptCount() {}
        override suspend fun suppressReviewPrompts(max: Int) {}
        override suspend fun setTutorialSeen() {}
    }
}
