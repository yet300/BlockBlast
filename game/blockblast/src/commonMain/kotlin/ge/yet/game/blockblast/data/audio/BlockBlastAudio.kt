package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType

internal object BlockBlastAudio {
    val musicTracks = listOf(
        "block.mp3",
        "feltwood.mp3",
        "mossy.mp3",
    )

    fun soundFor(type: FeedbackType): String = "voice_${type.name.lowercase()}.mp3"
}
