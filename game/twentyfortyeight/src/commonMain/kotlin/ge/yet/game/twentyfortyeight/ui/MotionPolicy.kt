package ge.yet.game.twentyfortyeight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

internal sealed interface MotionPolicy {
    val usesSpatialMotion: Boolean
    val transitionDurationMs: Int

    data object Normal : MotionPolicy {
        const val slideStiffness: Float = 700f
        const val slideDampingRatio: Float = 1f
        const val mergeCompressMs: Int = 70
        const val mergePulseMs: Int = 110
        const val spawnMs: Int = 120
        const val scoreMs: Int = 160
        const val crownMs: Int = 220
        const val victoryMaxStaggerMs: Int = 80
        const val gameOverMs: Int = 180
        const val undoMs: Int = 160

        override val usesSpatialMotion: Boolean = true
        override val transitionDurationMs: Int = undoMs
    }

    data object Reduced : MotionPolicy {
        const val alphaMs: Int = 72

        override val usesSpatialMotion: Boolean = false
        override val transitionDurationMs: Int = alphaMs
    }
}

internal const val normalTransitionDurationMs: Long =
    MotionPolicy.Normal.undoMs.toLong()

internal fun motionPolicy(durationScale: Float): MotionPolicy =
    if (durationScale == 0f) MotionPolicy.Reduced else MotionPolicy.Normal

@Composable
internal fun rememberMotionPolicy(): MotionPolicy {
    val durationScale = rememberCoroutineScope()
        .coroutineContext[MotionDurationScale]
        ?.scaleFactor
        ?: 1f
    return motionPolicy(durationScale)
}

@Composable
internal fun Modifier.finiteEntryReveal(
    normalDurationMs: Int,
    delayMs: Int = 0,
): Modifier {
    require(normalDurationMs > 0)
    require(delayMs in 0..MotionPolicy.Normal.victoryMaxStaggerMs)
    val policy = rememberMotionPolicy()
    val progress = remember { Animatable(0f) }
    LaunchedEffect(policy) {
        progress.snapTo(0f)
        if (delayMs > 0) delay(delayMs.toLong())
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (policy.usesSpatialMotion) {
                    normalDurationMs
                } else {
                    MotionPolicy.Reduced.alphaMs
                },
                easing = LinearEasing,
            ),
        )
    }
    return graphicsLayer {
        alpha = progress.value
        if (policy.usesSpatialMotion) {
            translationY = (1f - progress.value) * 12.dp.toPx()
        }
    }
}
