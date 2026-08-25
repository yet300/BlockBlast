package ge.yet.game.twentyfortyeight.store

internal fun interface NewGameSeedSource {
    fun nextSeed(): Long
}
