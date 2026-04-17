package ge.yet3.blokblast.component.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import ge.yet3.blokblast.theme.CoralAccent
import ge.yet3.blokblast.theme.Ivory
import ge.yet3.blokblast.theme.TerracottaBrand
import ge.yet3.blokblast.theme.WarmSand
import kotlin.math.cos
import kotlin.math.sin

/**
 * Slow-moving warm "mesh gradient" used as the ambient layer behind every
 * full screen.  Blob alphas are automatically scaled by the luminance of
 * [baseColor]: light backgrounds get the full warm-cream effect; dark
 * backgrounds get very subtle undertones so foreground text stays readable.
 */
@Composable
fun AmbientMeshBackground(
    modifier: Modifier = Modifier,
    baseColor: Color = Ivory,
) {
    val transition = rememberInfiniteTransition(label = "ambient-mesh")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ambient-phase",
    )

    // Scale blob opacity: light canvas → full effect; dark canvas → subtle glow only.
    // luminance() returns 0 (black) … 1 (white).
    val lum = baseColor.luminance()
    val blobScale = if (lum > 0.15f) 1f else (lum / 0.15f).coerceIn(0f, 1f) * 0.35f + 0.05f

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(baseColor)
            .drawBehind { drawAmbientBlobs(phase, blobScale) },
    )
}

private fun DrawScope.drawAmbientBlobs(phase: Float, scale: Float) {
    val w = size.width
    val h = size.height
    val r = maxOf(w, h) * 0.9f
    val t = phase * (2.0 * kotlin.math.PI).toFloat()

    drawBlob(
        color = TerracottaBrand.copy(alpha = 0.18f * scale),
        center = Offset(
            x = w * (0.30f + 0.10f * cos(t)),
            y = h * (0.25f + 0.08f * sin(t * 1.10f)),
        ),
        radius = r,
    )
    drawBlob(
        // WarmSand blobs are very light — keep alpha modest even on light backgrounds
        color = WarmSand.copy(alpha = 0.55f * scale),
        center = Offset(
            x = w * (0.75f + 0.08f * cos(t * 0.80f + 1.7f)),
            y = h * (0.30f + 0.10f * sin(t * 0.90f + 0.5f)),
        ),
        radius = r * 0.95f,
    )
    drawBlob(
        color = CoralAccent.copy(alpha = 0.12f * scale),
        center = Offset(
            x = w * (0.20f + 0.12f * cos(t * 0.70f + 3.1f)),
            y = h * (0.78f + 0.08f * sin(t * 0.60f + 2.2f)),
        ),
        radius = r * 0.85f,
    )
    drawBlob(
        // Ivory blob is near-white — clamp hard on dark backgrounds
        color = Ivory.copy(alpha = 0.45f * scale),
        center = Offset(
            x = w * (0.80f + 0.10f * cos(t * 1.20f + 4.4f)),
            y = h * (0.82f + 0.08f * sin(t * 1.00f + 3.6f)),
        ),
        radius = r,
    )
}

private fun DrawScope.drawBlob(color: Color, center: Offset, radius: Float) {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(color, color.copy(alpha = 0f)),
            center = center,
            radius = radius,
        ),
        topLeft = Offset.Zero,
        size = size,
    )
}
