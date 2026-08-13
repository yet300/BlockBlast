package ge.yet.game.data.platform

import kotlin.random.Random

internal interface PlatformSoundPlayer {
    fun playSound(filename: String)
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
