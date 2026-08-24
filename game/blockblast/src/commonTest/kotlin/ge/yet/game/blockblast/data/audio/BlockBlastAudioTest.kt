package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import ge.yet.game.domain.repository.AudioRepository
import kotlin.test.Test
import kotlin.test.assertEquals

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
    fun player_routes_game_semantics_to_bundled_files() {
        val audio = RecordingAudioRepository()
        val player = DefaultBlockBlastAudioPlayer(audio)

        player.startMusic()
        player.playFeedback(FeedbackType.EXCELLENT)
        player.stopMusic()

        assertEquals(listOf(BlockBlastAudioAssets.music), audio.musicStarts)
        assertEquals(listOf("voice_excellent.mp3"), audio.sounds)
        assertEquals(1, audio.stopCount)
    }

    private class RecordingAudioRepository : AudioRepository {
        val sounds = mutableListOf<String>()
        val musicStarts = mutableListOf<List<String>>()
        var stopCount = 0

        override fun playSound(filename: String) {
            sounds += filename
        }

        override fun startMusic(tracks: List<String>) {
            musicStarts += tracks.toList()
        }

        override fun stopMusic() {
            stopCount += 1
        }

        override fun onAppBackground() = Unit
        override fun onAppForeground() = Unit
    }
}
