package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * CRT / Glitch effect overlay inspired by Sina Samaki's "Glitch Effect"
 * and "CRT Screen Effect" articles.
 *
 * When [GlitchState.trigger] is called, the composable renders a brief
 * (400ms) distortion: random horizontal slice offsets + scan-line overlay +
 * chromatic-aberration-like color shifts.  The intensity ramps up then
 * decays, driven by [GlitchState.intensity] (0f = idle, 1f = peak).
 *
 * This uses `drawWithContent` (not Canvas) — the composable's real content
 * is drawn first, then the glitch artifacts are composited on top.
 */
class GlitchState {
    val intensity = Animatable(0f)

    suspend fun trigger(durationMillis: Int = 400) {
        intensity.snapTo(1f)
        intensity.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis),
        )
    }
}

@Composable
fun rememberGlitchState(): GlitchState = remember { GlitchState() }

fun Modifier.glitchEffect(state: GlitchState): Modifier =
    this.drawWithContent {
        drawContent()

        val i = state.intensity.value
        if (i <= 0.01f) return@drawWithContent

        @OptIn(ExperimentalTime::class)
        val rng = Random(Clock.System.now().toEpochMilliseconds())
        val sliceCount = (6 * i).toInt().coerceAtLeast(1)

        // Chromatic aberration tinted overlays
        val aberrationOffset = 4f * i
        drawRect(
            color = Color.Red.copy(alpha = 0.08f * i),
            topLeft = Offset(-aberrationOffset, 0f),
            size = size,
            blendMode = BlendMode.Screen,
        )
        drawRect(
            color = Color.Cyan.copy(alpha = 0.06f * i),
            topLeft = Offset(aberrationOffset, 0f),
            size = size,
            blendMode = BlendMode.Screen,
        )

        // Random horizontal slice offsets (scan-line glitch)
        repeat(sliceCount) {
            val sliceY = rng.nextFloat() * size.height
            val sliceH = (4f + rng.nextFloat() * 12f) * i
            val sliceShift = (rng.nextFloat() - 0.5f) * 20f * i
            drawRect(
                color = Color.White.copy(alpha = 0.06f * i),
                topLeft = Offset(sliceShift, sliceY),
                size = Size(size.width, sliceH),
                blendMode = BlendMode.Overlay,
            )
        }

        // Scan-line overlay (faint horizontal stripes)
        val lineSpacing = 4f
        var y = 0f
        while (y < size.height) {
            drawRect(
                color = Color.Black.copy(alpha = 0.04f * i),
                topLeft = Offset(0f, y),
                size = Size(size.width, 1f),
            )
            y += lineSpacing
        }
    }

