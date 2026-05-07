package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Combo punch: a short white screen flash plus a tiny camera-style zoom-in
 * applied to the whole game container, fired on combo ≥ 2 to amplify the
 * "satisfying" payoff of chained line clears.
 */
class ComboPunchState {
    val flashAlpha = Animatable(0f)
    val zoom = Animatable(1f)
    /**
     * Origin of the radial flash in container-local pixels.
     * Null falls back to a full-bounds white wash (legacy behavior).
     */
    var flashOrigin: Offset? by mutableStateOf(null)
        private set
    /** Cached level so the modifier can scale flash radius to combo intensity. */
    var lastLevel: Int by mutableStateOf(0)
        private set

    suspend fun punch(level: Int, origin: Offset? = null) {
        flashOrigin = origin
        lastLevel = level
        val flashStrength = (0.10f + 0.06f * level).coerceAtMost(0.32f)
        val zoomTarget = (1f + 0.012f * level).coerceAtMost(1.045f)
        coroutineScope {
            launch {
                flashAlpha.animateTo(flashStrength, tween(70))
                flashAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            }
            launch {
                zoom.animateTo(zoomTarget, tween(110, easing = FastOutSlowInEasing))
                zoom.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            }
        }
    }
}

@Composable
fun rememberComboPunchState(): ComboPunchState = remember { ComboPunchState() }

/** Applies the camera-zoom factor of [state] to a container. */
fun Modifier.comboZoom(state: ComboPunchState): Modifier = this.graphicsLayer {
    val z = state.zoom.value
    scaleX = z
    scaleY = z
}

/**
 * Renders the combo flash overlay. If [ComboPunchState.flashOrigin] is set,
 * the flash is a radial bloom centered on that point (container-local px),
 * shrinking to a point with combo level boosting the radius. Otherwise it
 * falls back to a full-bounds white wash.
 */
fun Modifier.comboFlash(state: ComboPunchState): Modifier = this.drawWithContent {
    drawContent()
    val a = state.flashAlpha.value
    if (a <= 0f) return@drawWithContent
    val origin = state.flashOrigin
    if (origin == null) {
        drawRect(color = Color.White.copy(alpha = a))
    } else {
        // Radius scales with combo level — a single line is a focused puff,
        // a 5+ chain washes most of the visible board.
        val baseR = size.minDimension * 0.18f
        val radius = baseR * (1f + 0.35f * state.lastLevel).coerceAtMost(2.4f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = a),
                    Color.White.copy(alpha = a * 0.55f),
                    Color.Transparent,
                ),
                center = origin,
                radius = radius,
            ),
        )
    }
}
