package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Inner red vignette that fades in past 70% board fill and pulses faster as
 * the danger climbs. Drawn over the grid content — clipped to the grid's
 * rounded shape by the parent container.
 *
 * dangerLevel is the raw filled-cell ratio (0..1). Below 0.70 the modifier
 * is a no-op; from 0.70 to 1.0 the vignette ramps to full strength and the
 * pulse speeds up from ~1.4s to ~0.55s.
 */
@Composable
fun Modifier.dangerVignette(dangerLevel: Float): Modifier = composed {
    if (dangerLevel < 0.70f) return@composed this

    val intensity = ((dangerLevel - 0.70f) / 0.30f).coerceIn(0f, 1f)
    val periodMs = (1400 - (intensity * 850).toInt()).coerceAtLeast(550)

    val infinite = rememberInfiniteTransition(label = "danger")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dangerPulse",
    )

    drawWithContent {
        drawContent()
        val edgeAlpha = 0.55f * intensity * pulse
        // Radial gradient transparent at center, red at the edges. Radius
        // chosen so the falloff begins inside the grid and the corners sit
        // in the saturated zone.
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.Transparent,
                    Color(0xFFB71C1C).copy(alpha = 0f),
                    Color(0xFFB71C1C).copy(alpha = edgeAlpha),
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = maxOf(size.width, size.height) * 0.72f,
            ),
        )
    }
}
