package ge.yet.game.fruitmerge.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.tutorial_skip
import ge.yet.game.fruitmerge.session.TutorialStep
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs

private const val HAND_TIP_X = 0.41f
private const val HAND_TIP_Y = 0.05f

@Composable
internal fun FruitMergeTutorial(
    step: TutorialStep?,
    boardBoundsInViewport: Rect,
    previewX: Float,
    reducedMotion: Boolean,
    onSkip: () -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayedStep = remember { mutableStateOf<TutorialStep>(step ?: TutorialStep.Gesture) }
    val previousStep = remember { mutableStateOf<TutorialStep?>(step) }
    val burst = remember { Animatable(1f) }
    val motionMode = tutorialMotionMode(
        visible = step != null,
        boundsReady = boardBoundsInViewport != Rect.Zero,
        reducedMotion = reducedMotion,
    )

    LaunchedEffect(step, reducedMotion) {
        if (step != null) displayedStep.value = step
        if (step == TutorialStep.Traits) {
            delay(if (reducedMotion) 900 else 2_300)
            onComplete()
        }
        if (step == null && previousStep.value == TutorialStep.Traits && !reducedMotion) {
            burst.snapTo(0f)
            burst.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        previousStep.value = step
    }

    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = motionMode != TutorialMotionMode.HIDDEN,
            enter = fadeIn(tween(if (reducedMotion) 0 else 180)),
            exit = fadeOut(tween(if (reducedMotion) 0 else 240)),
        ) {
            TutorialLayer(
                step = displayedStep.value,
                boardBounds = boardBoundsInViewport,
                previewX = previewX,
                reducedMotion = motionMode == TutorialMotionMode.STATIC,
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
    val progress = remember { Animatable(if (reducedMotion) 0.58f else 0f) }
    val palette = rememberFruitMergePalette()
    LaunchedEffect(step, boardBounds, reducedMotion) {
        if (reducedMotion) {
            progress.snapTo(0.58f)
            return@LaunchedEffect
        }
        when (step) {
            TutorialStep.Traits -> progress.animateTo(1f, tween(1_500, easing = FastOutSlowInEasing))
            else -> while (true) {
                progress.snapTo(0f)
                delay(240)
                progress.animateTo(
                    1f,
                    tween(if (step == TutorialStep.Gesture) 900 else 720, easing = FastOutSlowInEasing),
                )
                delay(430)
            }
        }
    }

    val skipDescription = stringResource(Res.string.tutorial_skip)
    Box(modifier = modifier) {
        Canvas(Modifier.fillMaxSize()) {
            drawTutorialCard(step, boardBounds, previewX, progress.value, palette)
        }
        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(48.dp)
                .semantics {
                    testTag = FruitMergeTestTags.TutorialSkip
                    contentDescription = skipDescription
                }
                .clickable(onClick = onSkip),
            shape = CircleShape,
            color = palette.paper,
            contentColor = palette.coral,
            shadowElevation = 3.dp,
        ) {
            Canvas(Modifier.fillMaxSize().padding(13.dp)) {
                val width = 3.dp.toPx()
                repeat(2) { index ->
                    val x = size.width * (0.25f + index * 0.34f)
                    drawLine(palette.coral, Offset(x, size.height * 0.20f), Offset(x + size.width * 0.28f, size.height * 0.50f), width, StrokeCap.Round)
                    drawLine(palette.coral, Offset(x + size.width * 0.28f, size.height * 0.50f), Offset(x, size.height * 0.80f), width, StrokeCap.Round)
                }
            }
        }
    }
}

private fun DrawScope.drawTutorialCard(
    step: TutorialStep,
    board: Rect,
    previewX: Float,
    progress: Float,
    palette: FruitMergePalette,
) {
    val cardWidth = board.width * 0.78f
    val cardHeight = board.height * 0.34f
    val card = Rect(
        left = board.center.x - cardWidth * 0.5f,
        top = board.top + board.height * 0.31f,
        right = board.center.x + cardWidth * 0.5f,
        bottom = board.top + board.height * 0.31f + cardHeight,
    )
    val radius = cardHeight * 0.12f
    drawRoundRect(
        color = palette.ink.copy(alpha = 0.14f),
        topLeft = card.topLeft + Offset(0f, cardHeight * 0.035f),
        size = card.size,
        cornerRadius = CornerRadius(radius),
    )
    drawRoundRect(palette.paper, card.topLeft, card.size, CornerRadius(radius))
    drawRoundRect(
        color = palette.woodDark.copy(alpha = 0.68f),
        topLeft = card.topLeft,
        size = card.size,
        cornerRadius = CornerRadius(radius),
        style = Stroke((cardHeight * 0.018f).coerceAtLeast(1f)),
    )

    val activeIndex = when (step) {
        TutorialStep.Gesture -> 0
        TutorialStep.Merge -> 1
        TutorialStep.Traits -> 2
    }
    repeat(3) { index ->
        drawCircle(
            color = if (index == activeIndex) palette.coral else palette.woodDark.copy(alpha = 0.24f),
            radius = cardHeight * if (index == activeIndex) 0.027f else 0.020f,
            center = Offset(card.center.x + (index - 1) * cardWidth * 0.08f, card.top + cardHeight * 0.12f),
        )
    }

    when (step) {
        TutorialStep.Gesture -> drawGestureTutorial(card, previewX, progress, palette)
        TutorialStep.Merge -> drawMergeTutorial(card, progress, palette)
        TutorialStep.Traits -> drawTraitTutorial(card, progress, palette)
    }
}

private fun DrawScope.drawGestureTutorial(
    card: Rect,
    previewX: Float,
    progress: Float,
    palette: FruitMergePalette,
) {
    val railY = card.top + card.height * 0.35f
    val railStart = card.left + card.width * 0.16f
    val railEnd = card.right - card.width * 0.16f
    drawLine(palette.coral.copy(alpha = 0.46f), Offset(railStart, railY), Offset(railEnd, railY), card.height * 0.025f, StrokeCap.Round)
    val xProgress = (previewX.coerceIn(0.15f, 0.85f) * 0.35f + progress * 0.65f).coerceIn(0f, 1f)
    val fruitX = railStart + (railEnd - railStart) * xProgress
    drawFruit(FruitLevel.BLUEBERRY, Offset(fruitX, railY), card.height * 0.13f, 0f, 0f, 0f, progress, DangerVisual(0f, false), 1f)
    repeat(5) { index ->
        drawCircle(
            color = palette.coral.copy(alpha = 0.58f - index * 0.07f),
            radius = card.height * 0.012f,
            center = Offset(fruitX, railY + card.height * (0.16f + index * 0.08f)),
        )
    }
    val handSize = Size(card.height * 0.24f, card.height * 0.29f)
    val handTip = Offset(fruitX, card.bottom - card.height * 0.12f)
    translate(handTip.x - handSize.width * HAND_TIP_X, handTip.y - handSize.height * HAND_TIP_Y) {
        drawTutorialHand(handSize, palette.ink)
    }
}

private fun DrawScope.drawMergeTutorial(
    card: Rect,
    progress: Float,
    palette: FruitMergePalette,
) {
    val center = Offset(card.center.x, card.top + card.height * 0.57f)
    val travel = card.width * 0.23f * (1f - progress)
    val sourceRadius = card.height * 0.14f
    val resultAlpha = ((progress - 0.70f) / 0.30f).coerceIn(0f, 1f)
    val sourceAlpha = 1f - resultAlpha
    drawFruit(FruitLevel.RASPBERRY, center - Offset(travel, 0f), sourceRadius, 0f, 0f, progress, 0f, DangerVisual(0f, false), sourceAlpha)
    drawFruit(FruitLevel.RASPBERRY, center + Offset(travel, 0f), sourceRadius, 0f, 0f, progress, 1f, DangerVisual(0f, false), sourceAlpha)
    if (resultAlpha > 0f) {
        drawCircle(palette.coral.copy(alpha = 0.16f * resultAlpha), sourceRadius * 1.55f, center)
        drawFruit(FruitLevel.STRAWBERRY, center, sourceRadius * (0.9f + resultAlpha * 0.25f), 0f, 0f, 1f - resultAlpha, progress, DangerVisual(0f, false), resultAlpha)
    }
}

private fun DrawScope.drawTraitTutorial(
    card: Rect,
    progress: Float,
    palette: FruitMergePalette,
) {
    val y = card.top + card.height * 0.58f
    val radius = card.height * 0.12f
    val centers = listOf(
        Offset(card.left + card.width * 0.23f, y),
        Offset(card.center.x, y + card.height * 0.06f * progress),
        Offset(card.right - card.width * 0.23f, y),
    )
    drawFruit(FruitLevel.STRAWBERRY, centers[0], radius, 0f, 0f, 0f, progress, DangerVisual(0f, false), 1f)
    drawLine(palette.woodDark, Offset(centers[0].x - radius * 1.25f, y - radius), Offset(centers[0].x - radius * 1.25f, y + radius), radius * 0.16f, StrokeCap.Round)
    repeat(3) { index ->
        drawCircle(palette.coral.copy(alpha = 0.55f), radius * 0.07f, Offset(centers[0].x - radius * (0.96f + index * 0.10f), y + (index - 1) * radius * 0.34f))
    }

    drawFruit(FruitLevel.APPLE, centers[1], radius * 1.08f, 0f, progress * 0.3f, progress, progress, DangerVisual(0f, false), 1f)
    drawLine(palette.woodDark.copy(alpha = 0.66f), Offset(centers[1].x, y - radius * 1.70f), Offset(centers[1].x, y - radius * 1.20f), radius * 0.12f, StrokeCap.Round)
    drawLine(palette.woodDark.copy(alpha = 0.66f), Offset(centers[1].x, y - radius * 1.20f), Offset(centers[1].x - radius * 0.22f, y - radius * 1.42f), radius * 0.12f, StrokeCap.Round)
    drawLine(palette.woodDark.copy(alpha = 0.66f), Offset(centers[1].x, y - radius * 1.20f), Offset(centers[1].x + radius * 0.22f, y - radius * 1.42f), radius * 0.12f, StrokeCap.Round)

    val pulse = 1f + progress * 0.16f
    drawCircle(palette.leaf.copy(alpha = 0.16f * (1f - progress * 0.35f)), radius * 1.55f * pulse, centers[2])
    drawFruit(FruitLevel.WATERMELON, centers[2], radius * 1.15f, 0f, 0f, progress, progress, DangerVisual(0f, false), 1f)
}

private fun DrawScope.drawTutorialHand(handSize: Size, ink: Color) {
    val w = handSize.width
    val h = handSize.height
    drawTutorialHandShapes(w, h, ink.copy(alpha = 0.20f), Offset(w * 0.04f, h * 0.05f))
    drawTutorialHandShapes(w, h, Color.White, Offset.Zero)
    drawTutorialHandShapes(w, h, ink.copy(alpha = 0.62f), Offset.Zero, outline = true)
}

private fun DrawScope.drawTutorialHandShapes(
    w: Float,
    h: Float,
    color: Color,
    offset: Offset,
    outline: Boolean = false,
) {
    val style = if (outline) Stroke((w * 0.045f).coerceAtLeast(1f)) else Fill
    drawRoundRect(color, Offset(w * 0.16f + offset.x, h * 0.40f + offset.y), Size(w * 0.74f, h * 0.58f), CornerRadius(w * 0.22f), style = style)
    drawRoundRect(color, Offset(w * 0.30f + offset.x, h * 0.02f + offset.y), Size(w * 0.22f, h * 0.52f), CornerRadius(w * 0.11f), style = style)
    rotate(-28f, Offset(w * 0.24f + offset.x, h * 0.62f + offset.y)) {
        drawRoundRect(color, Offset(w * 0.02f + offset.x, h * 0.52f + offset.y), Size(w * 0.40f, w * 0.22f), CornerRadius(w * 0.11f), style = style)
    }
}

internal enum class TutorialMotionMode { HIDDEN, STATIC, ANIMATED }

internal fun tutorialMotionMode(
    visible: Boolean,
    boundsReady: Boolean,
    reducedMotion: Boolean,
): TutorialMotionMode = when {
    !visible || !boundsReady -> TutorialMotionMode.HIDDEN
    reducedMotion -> TutorialMotionMode.STATIC
    else -> TutorialMotionMode.ANIMATED
}

@Composable
private fun TutorialCompletionBurst(progress: Float, modifier: Modifier = Modifier) {
    val palette = rememberFruitMergePalette()
    val colors = listOf(palette.coral, palette.leaf, palette.wood, Color(0xFFF5B642))
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
