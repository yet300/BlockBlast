package ge.yet3.blokblast.component.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.animation.Direction
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.StackAnimator
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.PredictiveBackAnimatable
import com.arkivanov.decompose.extensions.compose.stack.animation.predictiveback.predictiveBackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimator
import com.arkivanov.essenty.backhandler.BackEvent
import com.arkivanov.essenty.backhandler.BackHandler

/**
 * iOS-style predictive back animation.
 *
 * Replacement for Decompose 3.5.0's default [predictiveBackAnimation], which
 * crashes on cancelled back gestures with
 *   `IllegalArgumentException: Corner size in Px can't be negative`.
 *
 * The default animatable animates a RoundedCornerShape's corner radius via a
 * spring that overshoots below 0 on cancel, then hands that negative value to
 * RoundedCornerShape.createOutline which throws. This implementation uses only
 * translation (and an alpha-coerced dim layer) — no animated shape, so any
 * spring overshoot is harmless.
 */
@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun <C : Any, T : Any> cupertinoPredictiveBackAnimation(
    backHandler: BackHandler,
    onBack: () -> Unit,
    fallbackAnimation: StackAnimation<C, T>? = stackAnimation(
        animator = cupertinoStackAnimator(),
        disableInputDuringAnimation = true,
    ),
): StackAnimation<C, T> = predictiveBackAnimation(
    backHandler = backHandler,
    fallbackAnimation = fallbackAnimation,
    onBack = onBack,
    selector = { initialBackEvent, _, _ ->
        cupertinoPredictiveBackAnimatable(initialBackEvent = initialBackEvent)
    },
)

@ExperimentalDecomposeApi
private fun cupertinoPredictiveBackAnimatable(
    initialBackEvent: BackEvent,
): PredictiveBackAnimatable = CupertinoPredictiveBackAnimatable(initialBackEvent)

@OptIn(ExperimentalDecomposeApi::class)
private class CupertinoPredictiveBackAnimatable(
    initialBackEvent: BackEvent,
) : PredictiveBackAnimatable {

    private val progressAnimatable = Animatable(initialValue = initialBackEvent.progress)

    @Suppress("unused")
    private var swipeEdge by mutableStateOf(initialBackEvent.swipeEdge)

    override val exitModifier: Modifier = Modifier
        .cupertinoPredictiveExit { progressAnimatable.value }

    override val enterModifier: Modifier
        get() = Modifier.cupertinoPredictiveEnter { progressAnimatable.value }

    override suspend fun animate(event: BackEvent) {
        swipeEdge = event.swipeEdge
        progressAnimatable.snapTo(targetValue = event.progress)
    }

    override suspend fun cancel() {
        progressAnimatable.animateTo(targetValue = 0f)
    }

    override suspend fun finish() {
        progressAnimatable.animateTo(targetValue = 1f)
    }
}

private fun cupertinoStackAnimator(
    animationSpec: FiniteAnimationSpec<Float> = cupertinoTween(durationMillis = 500),
): StackAnimator = stackAnimator(
    animationSpec = animationSpec,
) { factor, direction, content ->
    content(
        Modifier.composed {
            val layoutDirection = LocalLayoutDirection.current
            graphicsLayer {
                translationX = size.width * when (direction) {
                    Direction.ENTER_FRONT,
                    Direction.EXIT_FRONT -> factor

                    else -> factor * SlideFactor
                }
                if (layoutDirection == LayoutDirection.Rtl) {
                    translationX = -translationX
                }
            }
        },
    )
}

private fun Modifier.cupertinoPredictiveEnter(progress: () -> Float): Modifier = composed {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    graphicsLayer {
        translationX = (progress() - 1f) * SlideFactor * size.width
        if (isRtl) translationX = -translationX
    }.drawWithContent {
        drawContent()
        drawRect(Color.Black, alpha = ((1f - progress()) * SlideFactor).coerceIn(0f, 1f))
    }
}

private fun Modifier.cupertinoPredictiveExit(progress: () -> Float): Modifier = composed {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    graphicsLayer {
        translationX = progress() * size.width
        if (isRtl) translationX = -translationX
    }
}

private const val SlideFactor = 0.25f

private fun <T> cupertinoTween(
    durationMillis: Int = CupertinoTransitionDuration,
    delayMillis: Int = 0,
    easing: Easing = CupertinoEasing,
): TweenSpec<T> = tween(
    durationMillis = durationMillis,
    easing = easing,
    delayMillis = delayMillis,
)

private val CupertinoEasing = CubicBezierEasing(0.2833f, 0.99f, 0.31833f, 0.99f)
private const val CupertinoTransitionDuration = 400
