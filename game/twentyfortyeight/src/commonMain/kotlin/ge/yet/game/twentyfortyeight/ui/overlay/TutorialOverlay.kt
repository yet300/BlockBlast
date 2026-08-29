package ge.yet.game.twentyfortyeight.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.ui.motion.MotionPolicy
import kotlinx.coroutines.delay

internal enum class TutorialMotionMode { Hidden, Static, Animated }

internal fun tutorialMotionMode(
    visible: Boolean,
    active: Boolean,
    policy: MotionPolicy,
): TutorialMotionMode = when {
    !visible -> TutorialMotionMode.Hidden
    !active || !policy.usesSpatialMotion -> TutorialMotionMode.Static
    else -> TutorialMotionMode.Animated
}

@Composable
internal fun TutorialOverlay(
    visible: Boolean,
    active: Boolean,
    policy: MotionPolicy,
    modifier: Modifier = Modifier,
) {
    val mode = tutorialMotionMode(visible, active, policy)
    val progress = remember { Animatable(0.5f) }

    LaunchedEffect(mode) {
        progress.snapTo(if (mode == TutorialMotionMode.Animated) 0f else 0.5f)
        if (mode != TutorialMotionMode.Animated) return@LaunchedEffect

        while (true) {
            delay(TutorialPauseMs)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = TutorialSwipeMs,
                    easing = FastOutSlowInEasing,
                ),
            )
            delay(TutorialPauseMs / 2)
            progress.snapTo(0f)
        }
    }

    if (mode == TutorialMotionMode.Hidden) return

    val accent = MaterialTheme.colorScheme.primary
    val hand = MaterialTheme.colorScheme.onSurface
    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("tutorial")
            .semantics {
                traversalIndex = 5f
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("tutorial_illustration"),
        ) {
            val t = progress.value.coerceIn(0f, 1f)
            val start = Offset(size.width * 0.76f, size.height * 0.52f)
            val end = Offset(size.width * 0.24f, size.height * 0.52f)
            val finger = Offset(
                x = start.x + (end.x - start.x) * t,
                y = start.y + (end.y - start.y) * t,
            )
            val handWidth = minOf(size.width * 0.22f, 78.dp.toPx())
            val handHeight = handWidth * 1.28f

            drawRect(Color.Black.copy(alpha = 0.12f))
            drawSwipeGuide(start = start, end = end, color = accent)
            drawCircle(
                color = accent.copy(alpha = 0.18f),
                radius = handWidth * 0.34f,
                center = finger,
            )
            drawSwipeHand(
                tip = finger,
                width = handWidth,
                height = handHeight,
                color = hand,
            )
        }
    }
}

private fun DrawScope.drawSwipeGuide(
    start: Offset,
    end: Offset,
    color: Color,
) {
    val stroke = 4.dp.toPx()
    val arrowHead = 14.dp.toPx()
    val guide = color.copy(alpha = 0.8f)
    drawLine(
        color = guide,
        start = start,
        end = end,
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = guide,
        start = end,
        end = Offset(end.x + arrowHead, end.y - arrowHead * 0.7f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = guide,
        start = end,
        end = Offset(end.x + arrowHead, end.y + arrowHead * 0.7f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawSwipeHand(
    tip: Offset,
    width: Float,
    height: Float,
    color: Color,
) {
    val left = tip.x - width * 0.31f
    val top = tip.y - height * 0.05f
    drawHandShapes(
        left = left + width * 0.05f,
        top = top + height * 0.05f,
        width = width,
        height = height,
        color = Color.Black.copy(alpha = 0.24f),
    )
    drawHandShapes(
        left = left,
        top = top,
        width = width,
        height = height,
        color = color,
    )
}

private fun DrawScope.drawHandShapes(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    drawRoundRect(
        color = color,
        topLeft = Offset(left + width * 0.14f, top + height * 0.39f),
        size = Size(width * 0.76f, height * 0.57f),
        cornerRadius = CornerRadius(width * 0.2f, width * 0.2f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(left + width * 0.31f, top),
        size = Size(width * 0.23f, height * 0.53f),
        cornerRadius = CornerRadius(width * 0.115f, width * 0.115f),
    )
    rotate(
        degrees = -25f,
        pivot = Offset(left + width * 0.25f, top + height * 0.63f),
    ) {
        drawRoundRect(
            color = color,
            topLeft = Offset(left + width * 0.01f, top + height * 0.53f),
            size = Size(width * 0.43f, width * 0.23f),
            cornerRadius = CornerRadius(width * 0.115f, width * 0.115f),
        )
    }
}

private const val TutorialPauseMs = 380L
private const val TutorialSwipeMs = 820
