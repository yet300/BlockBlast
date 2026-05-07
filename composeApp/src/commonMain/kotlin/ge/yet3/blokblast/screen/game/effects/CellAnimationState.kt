package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Per-cell animation state. Drives the punch-bounce on placement and the
 * wave-pop on clear. Inspired by Block Blast (HungryStudio): cells over-shoot
 * on placement and scale-up + spin slightly before fading on clear.
 */
class CellAnimState {
    val scale = Animatable(1f)
    // Independent X/Y multipliers used to "squash" the cell on snap-in: a wide-flat
    // anticipation pose collapses into a tall-narrow recoil before settling at 1×1.
    // Final composed scale at render time is (scale * scaleX, scale * scaleY).
    val scaleX = Animatable(1f)
    val scaleY = Animatable(1f)
    val alpha = Animatable(1f)
    val flashAlpha = Animatable(0f)
    val rotation = Animatable(0f)
    val translateY = Animatable(0f)

    /** Drop/snap bounce with squash — pancake-flat → tall recoil → settle. */
    suspend fun popIn(delayMs: Long) {
        scale.snapTo(0.85f)
        scaleX.snapTo(1.25f)
        scaleY.snapTo(0.65f)
        alpha.snapTo(1f)
        rotation.snapTo(0f)
        delay(delayMs)
        coroutineScope {
            launch {
                scale.animateTo(1f, tween(140, easing = LinearOutSlowInEasing))
            }
            launch {
                // Squash → recoil → settle (parallel, slightly offset on rebound).
                scaleX.animateTo(0.92f, tween(110, easing = LinearOutSlowInEasing))
                scaleX.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
            }
            launch {
                scaleY.animateTo(1.18f, tween(110, easing = LinearOutSlowInEasing))
                scaleY.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
            }
        }
    }

    /**
     * Wave pop on line clear — quick flash, scale-up & slight rotation before
     * fading out. The [delayMs] stagger is applied by the caller so the wave
     * propagates along the cleared row/column.
     */
    suspend fun clear(delayMs: Long) {
        delay(delayMs)
        coroutineScope {
            launch {
                flashAlpha.animateTo(0.8f, tween(70))
                flashAlpha.animateTo(0f, tween(120))
            }
            launch {
                scale.animateTo(1.35f, tween(120, easing = LinearOutSlowInEasing))
                scale.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
            launch {
                // Tiny tilt that randomizes per-cell via the delay parity —
                // good-enough randomness without an extra source of state.
                val tilt = if ((delayMs / 30L) % 2L == 0L) 18f else -18f
                rotation.animateTo(tilt, tween(220, easing = FastOutSlowInEasing))
            }
            launch {
                delay(120)
                alpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
        }
    }

    suspend fun reset() {
        scale.snapTo(1f)
        scaleX.snapTo(1f)
        scaleY.snapTo(1f)
        alpha.snapTo(1f)
        flashAlpha.snapTo(0f)
        rotation.snapTo(0f)
        translateY.snapTo(0f)
    }

    /**
     * Collapse the cell — used on game-over to make the board "fall" in
     * staggered waves before the overlay appears. The fall distance is
     * supplied in pixels by the caller because cells don't know their
     * absolute size here.
     */
    suspend fun fall(delayMs: Long, distancePx: Float) {
        delay(delayMs)
        coroutineScope {
            launch {
                rotation.animateTo(
                    if (delayMs % 2L == 0L) 18f else -18f,
                    tween(620, easing = FastOutSlowInEasing),
                )
            }
            launch {
                translateY.animateTo(distancePx, tween(620, easing = FastOutSlowInEasing))
            }
            launch {
                delay(280)
                alpha.animateTo(0f, tween(280))
            }
        }
    }
}
