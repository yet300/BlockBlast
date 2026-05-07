package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
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

    suspend fun punch(level: Int) {
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

/** Renders the combo flash overlay (drawn on top, full bounds). */
fun Modifier.comboFlash(state: ComboPunchState): Modifier = this.drawWithContent {
    drawContent()
    val a = state.flashAlpha.value
    if (a > 0f) {
        drawRect(color = Color.White.copy(alpha = a))
    }
}
