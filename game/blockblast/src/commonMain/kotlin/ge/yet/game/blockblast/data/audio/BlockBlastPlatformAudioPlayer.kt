package ge.yet.game.blockblast.data.audio

import kotlin.random.Random

internal interface BlockBlastPlatformAudioPlayer {
    fun playVoice(filename: String)
    fun startMusic(tracks: List<String>)
    fun stopMusic()
    fun release()
}

internal fun nextTrackIndex(
    trackCount: Int,
    previous: Int,
    random: Random = Random,
): Int {
    if (trackCount <= 1) return 0
    var next = random.nextInt(trackCount)
    while (next == previous) next = random.nextInt(trackCount)
    return next
}
