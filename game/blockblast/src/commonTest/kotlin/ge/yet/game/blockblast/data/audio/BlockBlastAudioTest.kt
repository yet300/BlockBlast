package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class BlockBlastAudioTest {

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
}
