package ge.yet.game.blockblast.data.audio

import ge.yet.game.blockblast.domain.model.FeedbackType

internal object BlockBlastAudioAssets {
    val music: List<String> = listOf("block.mp3", "feltwood.mp3", "mossy.mp3")

    fun voice(type: FeedbackType): String = when (type) {
        FeedbackType.GOOD -> "voice_good.mp3"
        FeedbackType.GREAT -> "voice_great.mp3"
        FeedbackType.AMAZING -> "voice_amazing.mp3"
        FeedbackType.EXCELLENT -> "voice_excellent.mp3"
        FeedbackType.UNBELIEVABLE -> "voice_unbelievable.mp3"
    }
}
