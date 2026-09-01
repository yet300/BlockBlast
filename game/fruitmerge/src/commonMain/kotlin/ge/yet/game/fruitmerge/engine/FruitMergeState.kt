package ge.yet.game.fruitmerge.engine

enum class RunPhase {
    PLAYING,
    RESULT,
}

enum class TargetingMode {
    NONE,
    CLEAR,
}

data class FruitMergeState(
    val bodies: List<FruitBody> = emptyList(),
    val previewLevel: FruitLevel = FruitLevel.BLUEBERRY,
    val nextPreviewLevel: FruitLevel = FruitLevel.RASPBERRY,
    val previewX: Float = 0.5f,
    val random: RandomState = RandomState(1),
    val nextBodyId: Long = 1,
    val score: Long = 0,
    val bestScore: Long = 0,
    val bestImprovedInRun: Boolean = false,
    val freeClears: Int = FREE_CLEAR_COUNT,
    val freeShakes: Int = FREE_SHAKE_COUNT,
    val dangerSeconds: Float = 0f,
    val graceSeconds: Float = 0f,
    val dropCooldownSeconds: Float = 0f,
    val shakeStepsRemaining: Int = 0,
    val runOrdinal: Long = 1,
    val phase: RunPhase = RunPhase.PLAYING,
    val targetingMode: TargetingMode = TargetingMode.NONE,
) {
    companion object {
        const val FREE_CLEAR_COUNT: Int = 5
        const val FREE_SHAKE_COUNT: Int = 3
    }
}
