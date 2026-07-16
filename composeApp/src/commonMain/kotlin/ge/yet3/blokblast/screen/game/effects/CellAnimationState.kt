package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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

    /**
     * Drop/snap with a soft squash — barely-flat → very mild recoil → settle.
     *
     * Tuned for visual smoothness rather than punch: amplitudes are kept
     * small enough that the cell never visibly leaves its grid slot, easing
     * is FastOutSlowInEasing throughout (no linear "snap" feel), durations
     * are stretched to ~250ms total, and the recoil springs use a high
     * damping ratio so they settle without ringing.
     *
     * Previous more aggressive version (scaleX 1.25 / scaleY 0.65, recoil
     * 1.18) made adjacent cells of multi-cell pieces overlap each other
     * mid-animation — looked like ghost cells appearing.
     *
     * The per-cell (x+y) * 25L wave delay used at the call site was also
     * dropped: staggering the squash across cells of a single piece made
     * the visual collision much worse.
     */
    suspend fun popIn(delayMs: Long, spatialMotionEnabled: Boolean = true) {
        if (!spatialMotionEnabled) {
            reset()
            return
        }
        scale.snapTo(0.92f)
        scaleX.snapTo(1.05f)
        scaleY.snapTo(0.95f)
        alpha.snapTo(1f)
        rotation.snapTo(0f)
        delay(delayMs)
        coroutineScope {
            launch {
                scale.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            }
            launch {
                scaleX.animateTo(0.98f, tween(140, easing = FastOutSlowInEasing))
                scaleX.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 450f))
            }
            launch {
                scaleY.animateTo(1.02f, tween(140, easing = FastOutSlowInEasing))
                scaleY.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 450f))
            }
        }
    }

    /**
     * Restrained wave exit on line clear — quick flash and slight rotation before
     * fading out. The [delayMs] stagger is applied by the caller so the wave
     * propagates along the cleared row/column.
     */
    suspend fun clear(delayMs: Long, spatialMotionEnabled: Boolean = true) {
        delay(if (spatialMotionEnabled) delayMs else 0L)
        if (!spatialMotionEnabled) {
            alpha.animateTo(0f, tween(140))
            return
        }
        coroutineScope {
            launch {
                flashAlpha.animateTo(0.5f, tween(60))
                flashAlpha.animateTo(0f, tween(120))
            }
            launch {
                scale.animateTo(0.94f, tween(180, easing = FastOutSlowInEasing))
            }
            launch {
                val tilt = if ((delayMs / 30L) % 2L == 0L) 8f else -8f
                rotation.animateTo(tilt, tween(180, easing = FastOutSlowInEasing))
            }
            launch {
                delay(40)
                alpha.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
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
    suspend fun fall(
        delayMs: Long,
        distancePx: Float,
        spatialMotionEnabled: Boolean = true,
    ) {
        delay(if (spatialMotionEnabled) delayMs else 0L)
        if (!spatialMotionEnabled) {
            alpha.animateTo(0f, tween(140))
            return
        }
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
