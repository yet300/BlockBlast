package ge.yet.blokblast.data.platform

internal object MusicPlaylist {
    val TRACKS: List<String> = listOf(
        "block.mp3",
        "feltwood.mp3",
        "mossy.mp3",
    )

    /**
     * Returns a random track index that is not [previous]. Falls back to a
     * random index when the playlist has a single track.
     */
    fun nextIndex(previous: Int, random: kotlin.random.Random = kotlin.random.Random): Int {
        if (TRACKS.size <= 1) return 0
        var next = random.nextInt(TRACKS.size)
        while (next == previous) next = random.nextInt(TRACKS.size)
        return next
    }
}
