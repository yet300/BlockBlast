package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/**
 * Animated stripe sweep that plays over cleared rows/columns.
 *
 * Inspired by Sina Samaki's "Animated Stripes" — a bright highlight bar
 * sweeps horizontally (for row clears) or vertically (for column clears)
 * across the grid in ~350ms, giving a satisfying "wipe" effect.
 *
 * Call [ComboStripesState.sweep] to trigger; the Modifier renders the
 * sweeping bar at [ComboStripesState.progress] (0f–1f).
 */
class ComboStripesState {
    val progress = Animatable(0f)

    suspend fun sweep(durationMillis: Int = 350) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis, easing = LinearEasing),
        )
    }
}

@Composable
fun rememberComboStripesState(): ComboStripesState = remember { ComboStripesState() }

/**
 * Draws a horizontal sweep bar across the composable.
 * The bar is positioned at [state.progress] fraction of the width.
 */
fun Modifier.comboStripes(
    state: ComboStripesState,
    stripeColor: Color = Color.White,
): Modifier = this.drawWithContent {
    drawContent()

    val p = state.progress.value
    if (p <= 0f || p >= 1f) return@drawWithContent

    val barWidth = size.width * 0.15f
    val barX = p * (size.width + barWidth) - barWidth

    drawRect(
        color = stripeColor.copy(alpha = 0.35f * (1f - p)),
        topLeft = Offset(barX, 0f),
        size = Size(barWidth, size.height),
    )
}
