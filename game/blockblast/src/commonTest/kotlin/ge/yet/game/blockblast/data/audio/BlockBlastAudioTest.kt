package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.domain.repository.FeedbackPreferences
import ge.yet.game.miniapp.api.MiniAppVisibility
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import ge.yet.game.miniapp.testkit.MiniAppLifecycleHarness
import ge.yet.game.miniapp.testkit.MutableMiniAppVisibilitySource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BlockBlastAudioTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUpMainDispatcher() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Test
    fun playlist_contains_the_three_existing_tracks() {
        assertEquals(
            listOf("block.mp3", "feltwood.mp3", "mossy.mp3"),
            BlockBlastAudioAssets.music,
        )
    }

    @Test
    fun every_feedback_type_maps_to_its_existing_voice_asset() {
        assertEquals("voice_good.mp3", BlockBlastAudioAssets.voice(FeedbackType.GOOD))
        assertEquals("voice_great.mp3", BlockBlastAudioAssets.voice(FeedbackType.GREAT))
        assertEquals("voice_amazing.mp3", BlockBlastAudioAssets.voice(FeedbackType.AMAZING))
        assertEquals("voice_excellent.mp3", BlockBlastAudioAssets.voice(FeedbackType.EXCELLENT))
        assertEquals("voice_unbelievable.mp3", BlockBlastAudioAssets.voice(FeedbackType.UNBELIEVABLE))
    }

    @Test
    fun next_track_does_not_repeat_when_playlist_has_multiple_tracks() {
        val next = nextTrackIndex(trackCount = 3, previous = 1, random = Random(7))

        assertNotEquals(1, next)
        assertTrue(next in 0..2)
    }

    @Test
    fun feedback_routes_the_exact_voice_asset_only_while_active_and_enabled() =
        runTest(dispatcher) {
            val setup = setup()
            setup.player.playFeedback(FeedbackType.EXCELLENT)
            assertEquals(listOf("voice_excellent.mp3"), setup.platform.voices)

            setup.preferences.sfx.value = false
            setup.player.playFeedback(FeedbackType.GOOD)
            setup.preferences.sfx.value = true
            setup.visibility.set(MiniAppVisibility.OBSCURED)
            setup.player.playFeedback(FeedbackType.GREAT)

            assertEquals(listOf("voice_excellent.mp3"), setup.platform.voices)
            setup.destroy()
        }

    @Test
    fun requested_music_tracks_visibility_and_music_preference() = runTest(dispatcher) {
        val setup = setup()
        setup.player.startMusic()
        runCurrent()
        assertEquals(listOf(BlockBlastAudioAssets.music), setup.platform.starts)

        setup.visibility.set(MiniAppVisibility.OBSCURED)
        runCurrent()
        assertEquals(1, setup.platform.stopCount)

        setup.visibility.set(MiniAppVisibility.ACTIVE)
        runCurrent()
        assertEquals(2, setup.platform.starts.size)

        setup.preferences.music.value = false
        runCurrent()
        assertEquals(2, setup.platform.stopCount)
        setup.destroy()
    }

    @Test
    fun repeated_start_is_idempotent() = runTest(dispatcher) {
        val setup = setup()

        setup.player.startMusic()
        setup.player.startMusic()
        runCurrent()

        assertEquals(1, setup.platform.starts.size)
        setup.destroy()
    }

    @Test
    fun explicit_stop_prevents_visibility_from_restarting_music() = runTest(dispatcher) {
        val setup = setup()
        setup.player.startMusic()
        runCurrent()
        setup.player.stopMusic()
        runCurrent()
        val starts = setup.platform.starts.size

        setup.visibility.set(MiniAppVisibility.OBSCURED)
        setup.visibility.set(MiniAppVisibility.ACTIVE)
        runCurrent()

        assertEquals(starts, setup.platform.starts.size)
        setup.destroy()
    }

    @Test
    fun destroy_releases_once_and_ignores_late_commands() = runTest(dispatcher) {
        val setup = setup()
        setup.destroy()
        setup.destroy()
        setup.player.startMusic()
        setup.player.playFeedback(FeedbackType.AMAZING)
        runCurrent()

        assertEquals(1, setup.platform.releaseCount)
        assertTrue(setup.platform.starts.isEmpty())
        assertTrue(setup.platform.voices.isEmpty())
    }

    @Test
    fun feedback_types_map_to_typed_procedural_effects() {
        assertEquals("feedback_amazing", BlockBlastAudio.sfxName(FeedbackType.AMAZING).value)
        assertEquals("feedback_good", BlockBlastAudio.sfxName(FeedbackType.GOOD).value)
        assertEquals("feedback_great", BlockBlastAudio.sfxName(FeedbackType.GREAT).value)
        assertEquals("feedback_excellent", BlockBlastAudio.sfxName(FeedbackType.EXCELLENT).value)
        assertEquals("feedback_unbelievable", BlockBlastAudio.sfxName(FeedbackType.UNBELIEVABLE).value)
    }

    @Test
    fun program_owns_music_and_every_feedback_effect_without_audio_files() {
        val program = BlockBlastAudio.program

        assertTrue(program.musicTracks.isNotEmpty())
        assertEquals(
            FeedbackType.entries.map(BlockBlastAudio::sfxName).toSet(),
            program.soundEffects.map { it.name }.toSet(),
        )
    }

    @Test
    fun player_routes_game_semantics_through_the_session_audio_facade() {
        val audio = RecordingMiniAppAudio()
        val player = DefaultBlockBlastAudioPlayer(audio)

        player.startMusic()
        player.playFeedback(FeedbackType.EXCELLENT)
        player.stopMusic()

        assertEquals(listOf(BlockBlastAudio.program), audio.musicPrograms)
        assertEquals(listOf(SfxName("feedback_excellent")), audio.sfxNames)
        assertEquals(1, audio.stopCount)
    }

    private class RecordingMiniAppAudio : MiniAppAudio {
        val musicPrograms = mutableListOf<AudioProgram>()
        val sfxNames = mutableListOf<SfxName>()
        var stopCount = 0

        override fun playMusic(program: AudioProgram): AudioCommandResult =
            AudioCommandResult.Accepted.also { musicPrograms += program }

        override fun stopMusic(fadeOut: AudioDuration): AudioCommandResult =
            AudioCommandResult.Accepted.also { stopCount += 1 }

        override fun playSfx(program: AudioProgram, name: SfxName): AudioCommandResult =
            AudioCommandResult.Accepted.also { sfxNames += name }

        override fun setControl(name: AudioControlName, value: Float): AudioCommandResult =
            AudioCommandResult.Accepted
    }

    private fun setup(): AudioSetup {
        val lifecycle = MiniAppLifecycleHarness().also { it.resume() }
        val visibility = MutableMiniAppVisibilitySource()
        val preferences = MutableFeedbackPreferences()
        val platform = RecordingPlatformAudioPlayer()
        return AudioSetup(
            lifecycle = lifecycle,
            visibility = visibility,
            preferences = preferences,
            platform = platform,
            player = DefaultBlockBlastFileAudioPlayer(
                platform = platform,
                preferences = preferences,
                visibility = visibility,
                componentContext = lifecycle.componentContext,
            ),
        )
    }

    private data class AudioSetup(
        val lifecycle: MiniAppLifecycleHarness,
        val visibility: MutableMiniAppVisibilitySource,
        val preferences: MutableFeedbackPreferences,
        val platform: RecordingPlatformAudioPlayer,
        val player: DefaultBlockBlastFileAudioPlayer,
    ) {
        fun destroy() = lifecycle.destroy()
    }

    private class MutableFeedbackPreferences : FeedbackPreferences {
        val music = MutableStateFlow(true)
        val sfx = MutableStateFlow(true)
        private val vibration = MutableStateFlow(true)
        override val musicEnabled: StateFlow<Boolean> = music.asStateFlow()
        override val sfxEnabled: StateFlow<Boolean> = sfx.asStateFlow()
        override val vibrationEnabled: StateFlow<Boolean> = vibration.asStateFlow()
    }

    private class RecordingPlatformAudioPlayer : BlockBlastPlatformAudioPlayer {
        val voices = mutableListOf<String>()
        val starts = mutableListOf<List<String>>()
        var stopCount = 0
        var releaseCount = 0

        override fun playVoice(filename: String) {
            voices += filename
        }

        override fun startMusic(tracks: List<String>) {
            starts += tracks.toList()
        }

        override fun stopMusic() {
            stopCount += 1
        }

        override fun release() {
            releaseCount += 1
        }
    }
}
