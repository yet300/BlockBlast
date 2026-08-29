package ge.yet.game.fruitmerge.engine

enum class FruitLevel(
    val radius: Float,
    val mass: Float,
    val mergeScore: Long,
    val spawnWeight: Int,
) {
    BLUEBERRY(0.035f, 1.0f, 2, 34),
    CHERRY(0.045f, 1.3f, 5, 27),
    STRAWBERRY(0.058f, 1.8f, 12, 20),
    PLUM(0.073f, 2.5f, 26, 12),
    MANDARIN(0.089f, 3.4f, 55, 7),
    APPLE(0.108f, 4.8f, 115, 0),
    PEAR(0.128f, 6.5f, 240, 0),
    PEACH(0.151f, 8.7f, 500, 0),
    PINEAPPLE(0.178f, 11.5f, 1_050, 0),
    MELON(0.210f, 15.0f, 2_200, 0),
    ;

    fun nextOrNull(): FruitLevel? = entries.getOrNull(ordinal + 1)

    companion object {
        val spawnable: List<FruitLevel> = entries.filter { level -> level.spawnWeight > 0 }
        val totalSpawnWeight: Int = spawnable.sumOf(FruitLevel::spawnWeight)
    }
}
