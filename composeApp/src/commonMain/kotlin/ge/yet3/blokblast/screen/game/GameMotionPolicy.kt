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

internal data class OneShotMotionDecision(
    val isNewEvent: Boolean,
    val shouldRunMotion: Boolean,
)

/**
 * Consumes each event identity once, independently from whether motion is currently allowed.
 *
 * This prevents a retained event from replaying when reduced motion changes while still letting
 * callers preserve non-motion feedback for a newly consumed event.
 */
internal class OneShotMotionGate<K> {
    private var hasConsumedEvent = false
    private var consumedIdentity: K? = null

    fun consume(eventIdentity: K, motionEnabled: Boolean): OneShotMotionDecision {
        val isNewEvent = !hasConsumedEvent || consumedIdentity != eventIdentity
        if (isNewEvent) {
            hasConsumedEvent = true
            consumedIdentity = eventIdentity
        }
        return OneShotMotionDecision(
            isNewEvent = isNewEvent,
            shouldRunMotion = isNewEvent && motionEnabled,
        )
    }

    fun reset() {
        hasConsumedEvent = false
        consumedIdentity = null
    }
}

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
