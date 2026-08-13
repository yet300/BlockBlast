package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType
import kotlin.test.Test
import kotlin.test.assertEquals

class BlockBlastAudioTest {

    @Test
    fun feedback_types_map_to_owned_voice_assets() {
        assertEquals("voice_amazing.mp3", BlockBlastAudio.soundFor(FeedbackType.AMAZING))
        assertEquals("voice_good.mp3", BlockBlastAudio.soundFor(FeedbackType.GOOD))
        assertEquals("voice_great.mp3", BlockBlastAudio.soundFor(FeedbackType.GREAT))
        assertEquals("voice_excellent.mp3", BlockBlastAudio.soundFor(FeedbackType.EXCELLENT))
        assertEquals(
            "voice_unbelievable.mp3",
            BlockBlastAudio.soundFor(FeedbackType.UNBELIEVABLE),
        )
    }

    @Test
    fun music_playlist_is_owned_by_blockblast() {
        assertEquals(
            listOf("block.mp3", "feltwood.mp3", "mossy.mp3"),
            BlockBlastAudio.musicTracks,
        )
    }
}
