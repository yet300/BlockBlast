package ge.yet3.blokblast.screen.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale

/**
 * Central decisions for optional game-screen motion.
 *
 * Static visual feedback remains the responsibility of each effect; this policy only determines
 * whether an effect is allowed to run a repeating or spatial animation.
 */
internal data class GameMotionPolicy(
    val animateHoverPulse: Boolean,
    val animatePredictionPulse: Boolean,
    val animateBorderGlow: Boolean,
    val spatialMotionEnabled: Boolean,
)

internal fun gameMotionPolicy(
    comboLevel: Int,
    hasDragHoverTarget: Boolean,
    hasPrediction: Boolean,
    reducedMotion: Boolean,
): GameMotionPolicy = GameMotionPolicy(
    animateHoverPulse = hasDragHoverTarget && !reducedMotion,
    animatePredictionPulse = hasPrediction && !reducedMotion,
    animateBorderGlow = comboLevel > 0 && !reducedMotion,
    spatialMotionEnabled = !reducedMotion,
)

internal fun MotionDurationScale?.isReducedMotion(): Boolean =
    this?.scaleFactor == 0f

/** Reads the duration scale installed in Compose's coroutine context. */
@Composable
internal fun rememberReducedMotion(): Boolean =
    rememberCoroutineScope().coroutineContext[MotionDurationScale].isReducedMotion()
