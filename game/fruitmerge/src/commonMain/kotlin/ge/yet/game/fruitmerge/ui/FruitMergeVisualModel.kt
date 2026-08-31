package ge.yet.game.fruitmerge.ui

internal data class DangerVisual(
    val intensity: Float,
    val crying: Boolean,
)

internal enum class FruitExpression {
    RESTING,
    FALLING,
    IMPACT,
    MERGING,
    CRYING,
}

internal fun fruitExpression(
    verticalVelocity: Float,
    impact: Float,
    danger: DangerVisual,
    merging: Boolean,
): FruitExpression = when {
    danger.crying -> FruitExpression.CRYING
    merging -> FruitExpression.MERGING
    impact >= 0.55f -> FruitExpression.IMPACT
    kotlin.math.abs(verticalVelocity) >= 0.30f -> FruitExpression.FALLING
    else -> FruitExpression.RESTING
}

internal fun dangerVisual(
    topY: Float,
    dangerY: Float,
    hasJoinedPile: Boolean,
): DangerVisual {
    if (!hasJoinedPile) return DangerVisual(intensity = 0f, crying = false)
    val distanceBelowLine = topY - dangerY
    val intensity = ((DANGER_WARNING_BAND - distanceBelowLine) / DANGER_WARNING_BAND)
        .coerceIn(0f, 1f)
    return DangerVisual(
        intensity = intensity,
        crying = distanceBelowLine <= DANGER_CRY_THRESHOLD,
    )
}

private const val DANGER_WARNING_BAND = 0.08f
private const val DANGER_CRY_THRESHOLD = 0.025f
