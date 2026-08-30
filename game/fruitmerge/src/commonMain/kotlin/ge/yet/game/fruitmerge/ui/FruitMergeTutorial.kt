package ge.yet.game.fruitmerge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.tutorial_drag
import ge.yet.game.fruitmerge.generated.resources.tutorial_skip
import ge.yet.game.fruitmerge.generated.resources.tutorial_tap
import ge.yet.game.fruitmerge.session.TutorialStep
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToInt

private const val HAND_TIP_X = 0.41f
private const val HAND_TIP_Y = 0.05f

@Composable
internal fun FruitMergeTutorial(
    step: TutorialStep?,
    boardBoundsInViewport: Rect,
    previewX: Float,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayedStep = remember { mutableStateOf<TutorialStep>(step ?: TutorialStep.Tap) }
    val previousStep = remember { mutableStateOf<TutorialStep?>(step) }
    val burst = remember { Animatable(1f) }

    LaunchedEffect(step) {
        if (step != null) displayedStep.value = step
        if (step == null && previousStep.value == TutorialStep.Drag && !reducedMotion) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        }
        previousStep.value = step
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = step != null && boardBoundsInViewport != Rect.Zero,
            enter = fadeIn(tween(if (reducedMotion) 0 else 220)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 360)),
        ) {
            TutorialLayer(
                step = displayedStep.value,
                boardBounds = boardBoundsInViewport,
                previewX = previewX,
                reducedMotion = reducedMotion,
                onSkip = onSkip,
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { testTag = FruitMergeTestTags.Tutorial },
            )
        }
        if (burst.isRunning || burst.value < 1f) {
            TutorialCompletionBurst(progress = burst.value, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun TutorialLayer(
    step: TutorialStep,
    boardBounds: Rect,
    previewX: Float,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (reducedMotion) 0.5f else 0f) }
    val pressed = remember { mutableStateOf(false) }
    LaunchedEffect(step, boardBounds, reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(0.5f)
            return@LaunchedEffect
        }
        while (true) {
            progress.snapTo(0f)
            pressed.value = false
            delay(300)
            pressed.value = true
            delay(220)
            when (step) {
                TutorialStep.Tap -> progress.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                TutorialStep.Drag -> progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            }
            pressed.value = false
            delay(580)
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val onPrimary = MaterialTheme.colorScheme.onPrimary
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.54f)
    val start = tutorialStart(step, boardBounds, previewX)
    val end = tutorialEnd(step, boardBounds, previewX)
    val handPoint = Offset(
        x = start.x + (end.x - start.x) * progress.value,
        y = start.y + (end.y - start.y) * progress.value,
    )

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithCache {
                    onDrawWithContent {
                        drawRect(scrim)
                        val spotlight = tutorialSpotlight(step, boardBounds, end)
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = spotlight.topLeft,
                            size = spotlight.size,
                            cornerRadius = CornerRadius(spotlight.height * 0.35f),
                            blendMode = BlendMode.Clear,
                        )
                        drawRoundRect(
                            color = primary.copy(alpha = 0.8f),
                            topLeft = spotlight.topLeft,
                            size = spotlight.size,
                            cornerRadius = CornerRadius(spotlight.height * 0.35f),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                        drawContent()
                    }
                },
        ) {
            val dotRadius = size.minDimension * 0.007f
            repeat(8) { index ->
                val fraction = index / 7f
                drawCircle(
                    color = primary.copy(alpha = 0.76f),
                    radius = dotRadius,
                    center = Offset(
                        x = end.x,
                        y = end.y + boardBounds.height * (0.12f + fraction * 0.43f),
                    ),
                )
            }
        }

        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .semantics { testTag = FruitMergeTestTags.TutorialSkip },
        ) {
            Text(
                text = "${stringResource(Res.string.tutorial_skip)} »",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 72.dp)
                .padding(horizontal = 28.dp)
                .widthIn(max = 480.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = primary,
            shadowElevation = 8.dp,
        ) {
            Text(
                text = stringResource(
                    when (step) {
                        TutorialStep.Tap -> Res.string.tutorial_tap
                        TutorialStep.Drag -> Res.string.tutorial_drag
                    },
                ),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 18.dp),
                color = onPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        Canvas(
            modifier = Modifier
                .offset {
                    IntOffset(
                        x = (handPoint.x - 64.dp.toPx() * HAND_TIP_X).roundToInt(),
                        y = (handPoint.y - 74.dp.toPx() * HAND_TIP_Y).roundToInt(),
                    )
                }
                .size(width = 64.dp, height = 74.dp)
                .graphicsLayer {
                    val scale = if (pressed.value) 0.9f else 1f
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(HAND_TIP_X, HAND_TIP_Y)
                },
        ) {
            if (pressed.value || step == TutorialStep.Tap) {
                drawCircle(
                    color = primary.copy(alpha = 0.30f),
                    radius = size.width * (0.22f + progress.value * 0.14f),
                    center = Offset(size.width * HAND_TIP_X, size.height * HAND_TIP_Y),
                )
            }
            drawTutorialHand()
        }
    }
}

private fun tutorialSpotlight(step: TutorialStep, board: Rect, end: Offset): Rect =
    if (step == TutorialStep.Tap) {
        Rect(
            left = end.x - board.width * 0.10f,
            top = end.y - board.height * 0.10f,
            right = end.x + board.width * 0.10f,
            bottom = end.y + board.height * 0.10f,
        )
    } else {
        Rect(
            left = board.left + board.width * 0.08f,
            top = board.top + board.height * 0.015f,
            right = board.right - board.width * 0.08f,
            bottom = board.top + board.height * 0.20f,
        )
    }

private fun tutorialStart(step: TutorialStep, board: Rect, previewX: Float): Offset = when (step) {
    TutorialStep.Tap -> Offset(
        x = board.left + board.width * previewX.coerceIn(0.12f, 0.88f),
        y = board.top + board.height * 0.42f,
    )
    TutorialStep.Drag -> Offset(
        x = board.left + board.width * 0.24f,
        y = board.top + board.height * 0.13f,
    )
}

private fun tutorialEnd(step: TutorialStep, board: Rect, previewX: Float): Offset = when (step) {
    TutorialStep.Tap -> Offset(
        x = board.left + board.width * previewX.coerceIn(0.12f, 0.88f),
        y = board.top + board.height * 0.20f,
    )
    TutorialStep.Drag -> Offset(
        x = board.left + board.width * 0.74f,
        y = board.top + board.height * 0.13f,
    )
}

private fun DrawScope.drawTutorialHand() {
    val w = size.width
    val h = size.height
    drawTutorialHandShapes(w, h, Color.Black.copy(alpha = 0.22f), Offset(w * 0.04f, h * 0.05f))
    drawTutorialHandShapes(w, h, Color.White, Offset.Zero)
}

private fun DrawScope.drawTutorialHandShapes(w: Float, h: Float, color: Color, offset: Offset) {
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.16f + offset.x, h * 0.40f + offset.y),
        size = Size(w * 0.74f, h * 0.58f),
        cornerRadius = CornerRadius(w * 0.22f),
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.30f + offset.x, h * 0.02f + offset.y),
        size = Size(w * 0.22f, h * 0.52f),
        cornerRadius = CornerRadius(w * 0.11f),
    )
    rotate(-28f, Offset(w * 0.24f + offset.x, h * 0.62f + offset.y)) {
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.02f + offset.x, h * 0.52f + offset.y),
            size = Size(w * 0.40f, w * 0.22f),
            cornerRadius = CornerRadius(w * 0.11f),
        )
    }
}

@Composable
private fun TutorialCompletionBurst(progress: Float, modifier: Modifier = Modifier) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        Color(0xFFF5B642),
        Color(0xFF78A65A),
    )
    Canvas(modifier) {
        repeat(12) { index ->
            val direction = index - 5.5f
            val travel = size.minDimension * 0.32f * progress
            drawCircle(
                color = colors[index % colors.size].copy(alpha = 1f - progress),
                radius = size.minDimension * 0.008f,
                center = Offset(
                    x = center.x + direction * travel * 0.16f,
                    y = center.y - travel + abs(direction) * travel * 0.08f,
                ),
            )
        }
    }
}
