package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.miniapp.audio.AudioCommandResult
import ge.yet.game.miniapp.audio.AudioControlName
import ge.yet.game.miniapp.audio.AudioDuration
import ge.yet.game.miniapp.audio.AudioProgram
import ge.yet.game.miniapp.audio.MiniAppAudio
import ge.yet.game.miniapp.audio.SfxName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlockBlastAudioTest {

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
