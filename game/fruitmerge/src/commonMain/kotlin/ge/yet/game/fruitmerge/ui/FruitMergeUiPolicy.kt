package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import kotlin.math.sin

internal data class ShakeVisualTransform(
    val translationXDp: Float,
    val rotationDegrees: Float,
)

internal fun shakeVisualTransform(
    stepsRemaining: Int,
    reducedMotion: Boolean,
): ShakeVisualTransform {
    if (stepsRemaining <= 0 || reducedMotion) return ShakeVisualTransform(0f, 0f)
    val bounded = stepsRemaining.coerceAtMost(FruitMergeEngine.SHAKE_DURATION_STEPS)
    val elapsed = FruitMergeEngine.SHAKE_DURATION_STEPS - bounded
    val envelope = bounded.toFloat() / FruitMergeEngine.SHAKE_DURATION_STEPS
    return ShakeVisualTransform(
        translationXDp = sin(elapsed * 1.73f) * 6f * envelope,
        rotationDegrees = sin(elapsed * 0.91f) * 0.85f * envelope,
    )
}
