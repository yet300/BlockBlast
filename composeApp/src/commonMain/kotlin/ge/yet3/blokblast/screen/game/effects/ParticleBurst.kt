package ge.yet3.blokblast.screen.game.effects

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lightweight particle system for line-clear bursts and the radial shockwave
 * fired at row/column intersections. All physics happens in the grid's local
 * coordinate space (0..1 normalized), so the modifier stays grid-size-agnostic.
 *
 * One state object covers both burst particles and the shockwave ring.
 */
class ParticleBurstState {
    val particles = mutableStateListOf<Particle>()
    val shockwaves = mutableStateListOf<Shockwave>()

    /**
     * Spawn a fan of particles at [cellGridX], [cellGridY] (cell-coords on the
     * 8×8 board). [color] paints the squares.
     */
    suspend fun burst(cellGridX: Int, cellGridY: Int, color: Color, count: Int = 6) {
        coroutineScope {
            repeat(count) { i ->
                val angle = (i.toFloat() / count) * (2 * kotlin.math.PI).toFloat() +
                    Random.nextFloat() * 0.5f
                val speed = 0.4f + Random.nextFloat() * 0.4f
                val particle = Particle(
                    startX = (cellGridX + 0.5f) / 8f,
                    startY = (cellGridY + 0.5f) / 8f,
                    velocityX = cos(angle) * speed,
                    velocityY = sin(angle) * speed,
                    color = color,
                    size = 0.018f + Random.nextFloat() * 0.012f,
                )
                particles.add(particle)
                launch {
                    particle.progress.animateTo(1f, tween(700, easing = LinearEasing))
                    particles.remove(particle)
                }
            }
        }
    }

    suspend fun shockwave(cellGridX: Int, cellGridY: Int, color: Color) {
        val sw = Shockwave(
            cx = (cellGridX + 0.5f) / 8f,
            cy = (cellGridY + 0.5f) / 8f,
            color = color,
        )
        shockwaves.add(sw)
        sw.progress.animateTo(1f, tween(450, easing = LinearEasing))
        shockwaves.remove(sw)
    }
}

class Particle(
    val startX: Float,
    val startY: Float,
    val velocityX: Float,
    val velocityY: Float,
    val color: Color,
    val size: Float,
) {
    val progress = Animatable(0f)
}

class Shockwave(
    val cx: Float,
    val cy: Float,
    val color: Color,
) {
    val progress = Animatable(0f)
}

@Composable
fun rememberParticleBurstState(): ParticleBurstState = remember { ParticleBurstState() }

/**
 * Draws particles and shockwaves on top of the grid. Coordinates are
 * normalized 0..1 of the modifier's draw size.
 */
fun Modifier.particleBurst(state: ParticleBurstState): Modifier = this.drawWithContent {
    drawContent()

    val w = size.width
    val h = size.height

    // Particles: ballistic with gravity. Position evolves with t in [0,1].
    state.particles.forEach { p ->
        val t = p.progress.value
        val x = p.startX + p.velocityX * t
        val y = p.startY + p.velocityY * t + 0.6f * t * t // gravity
        val alpha = (1f - t).coerceIn(0f, 1f)
        val s = p.size
        drawRect(
            color = p.color.copy(alpha = alpha),
            topLeft = Offset((x - s / 2f) * w, (y - s / 2f) * h),
            size = Size(s * w, s * h),
        )
    }

    // Shockwave: expanding ring (stroked), fading alpha.
    state.shockwaves.forEach { sw ->
        val t = sw.progress.value
        val maxR = 0.55f * w
        val radius = t * maxR
        val alpha = (1f - t).coerceIn(0f, 1f) * 0.6f
        drawCircle(
            color = sw.color.copy(alpha = alpha),
            radius = radius,
            center = Offset(sw.cx * w, sw.cy * h),
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 0.04f * w,
            ),
        )
    }
}
