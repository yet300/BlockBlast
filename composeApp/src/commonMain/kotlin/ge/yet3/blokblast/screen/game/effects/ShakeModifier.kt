package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Horizontal shake animation for invalid placement attempts.
 *
 * Inspired by Sina Samaki's "Shake Animations in Compose" —
 * uses a critically-damped spring on translationX to shake the
 * composable side-to-side and settle naturally.
 *
 * Call [ShakeState.shake] to trigger; the Modifier reads
 * [ShakeState.offsetX] each frame.
 */
class ShakeState {
    val offsetX = Animatable(0f)

    suspend fun shake(strength: Float = 18f) {
        offsetX.snapTo(strength)
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessHigh,
            ),
        )
    }
}

@Composable
fun rememberShakeState(): ShakeState = remember { ShakeState() }

fun Modifier.shake(state: ShakeState): Modifier =
    this.graphicsLayer { translationX = state.offsetX.value }
