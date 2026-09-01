package ge.yet.game.fruitmerge.ui

import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import kotlin.math.sin

internal data class ShakeVisualTransform(
    val translationXDp: Float,
    val rotationDegrees: Float,
)

internal data class MergeSqueeze(val scaleX: Float, val scaleY: Float)

internal enum class ScoreCardState { SCORE_ONLY, SCORE_AND_BEST, BEST_ONLY }

internal fun scoreCardState(bestScore: Long, bestImprovedInRun: Boolean): ScoreCardState = when {
    bestImprovedInRun -> ScoreCardState.BEST_ONLY
    bestScore > 0L -> ScoreCardState.SCORE_AND_BEST
    else -> ScoreCardState.SCORE_ONLY
}

internal fun mergePresentationProgress(elapsedSeconds: Float): Float =
    (elapsedSeconds / MERGE_PRESENTATION_SECONDS).coerceIn(0f, 1f)

internal fun mergeSqueeze(progress: Float): MergeSqueeze {
    val pulse = sin(progress.coerceIn(0f, 1f) * kotlin.math.PI.toFloat())
    return MergeSqueeze(scaleX = 1f + pulse * 0.28f, scaleY = 1f - pulse * 0.20f)
}

internal fun guideAlpha(cooldownSeconds: Float): Float =
    (1f - cooldownSeconds / GUIDE_FADE_SECONDS).coerceIn(0f, 1f)

internal fun crateHandleRotation(stepsRemaining: Int, reducedMotion: Boolean): Float =
    shakeVisualTransform(stepsRemaining, reducedMotion).rotationDegrees * 7f

internal fun shakeVisualTransform(
    stepsRemaining: Int,
    reducedMotion: Boolean,
): ShakeVisualTransform {
    if (stepsRemaining <= 0) return ShakeVisualTransform(0f, 0f)
    val bounded = stepsRemaining.coerceAtMost(FruitMergeEngine.SHAKE_DURATION_STEPS)
    val elapsed = FruitMergeEngine.SHAKE_DURATION_STEPS - bounded
    val envelope = 0.45f + 0.55f * bounded.toFloat() / FruitMergeEngine.SHAKE_DURATION_STEPS
    val motionScale = if (reducedMotion) REDUCED_MOTION_SCALE else 1f
    val lateralWave = sin(elapsed * 1.73f) + sin(elapsed * 2.91f) * 0.32f
    val rotationWave = sin(elapsed * 0.91f) + sin(elapsed * 2.17f) * 0.24f
    return ShakeVisualTransform(
        translationXDp = lateralWave * 11f * envelope * motionScale,
        rotationDegrees = rotationWave * 1.7f * envelope * motionScale,
    )
}

private const val REDUCED_MOTION_SCALE: Float = 0.22f
internal const val MERGE_PRESENTATION_SECONDS: Float = 0.145f
private const val GUIDE_FADE_SECONDS: Float = 0.25f
