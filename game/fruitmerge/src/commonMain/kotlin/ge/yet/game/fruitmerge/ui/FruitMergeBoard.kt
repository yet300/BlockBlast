package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.engine.FruitBody
import ge.yet.game.fruitmerge.engine.FruitLevel
import ge.yet.game.fruitmerge.engine.FruitMergeEngine
import ge.yet.game.fruitmerge.engine.FruitMergeState
import ge.yet.game.fruitmerge.engine.RunPhase
import ge.yet.game.fruitmerge.engine.TargetingMode
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun FruitMergeBoard(
    game: FruitMergeState,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    boardDescription: String,
    dangerDescription: String,
    onClearTarget: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dangerDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val targetingClear = game.targetingMode == TargetingMode.CLEAR
    val latestBodies = rememberUpdatedState(game.bodies)
    val latestClearTarget = rememberUpdatedState(onClearTarget)
    val shakeTransform = shakeVisualTransform(game.shakeStepsRemaining, reducedMotion)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = shakeTransform.translationXDp.dp.toPx()
                rotationZ = shakeTransform.rotationDegrees
            }
            .fruitMergeClearPointerInput(
                enabled = game.phase == RunPhase.PLAYING && targetingClear,
                bodies = { latestBodies.value },
                onClearTarget = { latestClearTarget.value(it) },
            )
            .semantics {
                contentDescription = if (game.dangerSeconds > 0f) {
                    "$boardDescription. $dangerDescription"
                } else {
                    boardDescription
                }
            },
    ) {
        val transform = BoardTransform(size)
        val cornerRadius = transform.side * 0.045f
        drawRoundRect(
            color = BoardCream,
            topLeft = transform.origin,
            size = Size(transform.side, transform.side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        )
        drawRoundRect(
            color = BoardOutline,
            topLeft = transform.origin,
            size = Size(transform.side, transform.side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            style = Stroke(width = transform.side * 0.009f),
        )

        val dangerY = transform.worldY(FruitMergeEngine.DANGER_Y)
        drawLine(
            color = if (game.dangerSeconds > 0f) DangerActive else DangerIdle,
            start = Offset(transform.origin.x + transform.side * 0.03f, dangerY),
            end = Offset(transform.origin.x + transform.side * 0.97f, dangerY),
            strokeWidth = transform.side * 0.006f,
            pathEffect = dangerDash,
        )

        if (game.phase == RunPhase.PLAYING && !targetingClear) {
            val previewCenter = transform.world(game.previewX, PREVIEW_Y)
            drawLine(
                color = BoardOutline.copy(alpha = 0.28f),
                start = Offset(previewCenter.x, previewCenter.y + game.previewLevel.radius * transform.side),
                end = Offset(previewCenter.x, transform.worldY(0.96f)),
                strokeWidth = transform.side * 0.003f,
                pathEffect = dangerDash,
            )
            drawFruit(
                level = game.previewLevel,
                center = previewCenter,
                radius = game.previewLevel.radius * transform.side,
                angleRadians = 0f,
                verticalVelocity = 0f,
                impact = 0f,
                facePhase = faceTimeSeconds + game.previewLevel.ordinal,
                anxious = false,
                alpha = 0.88f,
            )
        }

        for (body in game.bodies) {
            val anxious = body.position.y - body.level.radius < FruitMergeEngine.DANGER_Y
            drawFruit(
                level = body.level,
                center = transform.world(body.position.x, body.position.y),
                radius = body.level.radius * transform.side,
                angleRadians = body.angle,
                verticalVelocity = body.velocity.y,
                impact = body.impact,
                facePhase = if (reducedMotion) body.id.toFloat() else faceTimeSeconds + body.id * 0.37f,
                anxious = anxious,
                alpha = 1f,
            )
            if (targetingClear) {
                val pulse = if (reducedMotion) 1f else 0.94f + sin(faceTimeSeconds * 5f + body.id) * 0.06f
                drawCircle(
                    color = ClearTarget,
                    radius = body.level.radius * transform.side * 1.14f * pulse,
                    center = transform.world(body.position.x, body.position.y),
                    style = Stroke(width = transform.side * 0.006f),
                )
            }
        }

    }
}

@Composable
internal fun FruitPreview(
    level: FruitLevel,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = min(size.width, size.height) * 0.34f
        drawFruit(
            level = level,
            center = center,
            radius = radius,
            angleRadians = 0f,
            verticalVelocity = 0f,
            impact = 0f,
            facePhase = if (reducedMotion) level.ordinal.toFloat() else faceTimeSeconds + level.ordinal,
            anxious = false,
            alpha = 1f,
        )
    }
}

private fun Modifier.fruitMergeClearPointerInput(
    enabled: Boolean,
    bodies: () -> List<FruitBody>,
    onClearTarget: (Long) -> Unit,
): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = true)
        var last: PointerInputChange = down
        while (last.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            last = change
        }
        findClearTarget(last.position, size, bodies())?.let(onClearTarget)
    }
}

private fun findClearTarget(position: Offset, size: IntSize, bodies: List<FruitBody>): Long? {
    val side = min(size.width, size.height).toFloat().coerceAtLeast(1f)
    val origin = Offset((size.width - side) * 0.5f, (size.height - side) * 0.5f)
    val world = Offset((position.x - origin.x) / side, (position.y - origin.y) / side)
    var bestId: Long? = null
    var bestDistance = Float.POSITIVE_INFINITY
    for (body in bodies) {
        val dx = world.x - body.position.x
        val dy = world.y - body.position.y
        val distance = dx * dx + dy * dy
        val hitRadius = body.level.radius + CLEAR_TOUCH_MARGIN
        if (distance <= hitRadius * hitRadius && distance < bestDistance) {
            bestId = body.id
            bestDistance = distance
        }
    }
    return bestId
}

internal fun DrawScope.drawFruit(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    angleRadians: Float,
    verticalVelocity: Float,
    impact: Float,
    facePhase: Float,
    anxious: Boolean,
    alpha: Float,
) {
    val style = fruitVisualSpec(level)
    val squash = (abs(verticalVelocity) * 0.025f + impact * 0.10f).coerceIn(0f, 0.14f)
    val fruitSize = Size(radius * 2f * (1f + squash), radius * 2f * (1f - squash))
    val topLeft = Offset(center.x - fruitSize.width * 0.5f, center.y - fruitSize.height * 0.5f)

    drawFruitBody(level, center, radius, fruitSize, topLeft, style, alpha)
    drawOval(
        color = Color.White.copy(alpha = 0.16f * alpha),
        topLeft = Offset(center.x - radius * 0.62f, center.y - radius * 0.64f),
        size = Size(radius * 0.76f, radius * 0.52f),
    )
    drawOval(
        color = style.highlight.copy(alpha = 0.78f * alpha),
        topLeft = Offset(center.x - radius * 0.52f, center.y - radius * 0.52f),
        size = Size(radius * 0.34f, radius * 0.22f),
    )
    drawOval(
        color = style.blush.copy(alpha = 0.42f * alpha),
        topLeft = Offset(center.x - radius * 0.74f, center.y + radius * 0.08f),
        size = Size(radius * 0.32f, radius * 0.20f),
    )
    drawOval(
        color = style.blush.copy(alpha = 0.42f * alpha),
        topLeft = Offset(center.x + radius * 0.42f, center.y + radius * 0.08f),
        size = Size(radius * 0.32f, radius * 0.20f),
    )

    drawFruitTop(level, center, radius, angleRadians, alpha)

    val blink = (facePhase % 4.2f) < 0.12f
    val eyeY = center.y - radius * if (style.face == FruitFace.SLEEPY) 0.08f else 0.03f
    val eyeOffset = radius * if (style.face == FruitFace.SHY) 0.25f else 0.29f
    val eyeRadius = (radius * 0.075f).coerceAtLeast(1.15f)
    if (blink || impact > 0.76f) {
        val halfWidth = radius * 0.11f
        drawLine(
            color = FaceInk.copy(alpha = alpha),
            start = Offset(center.x - eyeOffset - halfWidth, eyeY),
            end = Offset(center.x - eyeOffset + halfWidth, eyeY),
            strokeWidth = (radius * 0.055f).coerceAtLeast(1f),
        )
        drawLine(
            color = FaceInk.copy(alpha = alpha),
            start = Offset(center.x + eyeOffset - halfWidth, eyeY),
            end = Offset(center.x + eyeOffset + halfWidth, eyeY),
            strokeWidth = (radius * 0.055f).coerceAtLeast(1f),
        )
    } else {
        drawCircle(FaceInk.copy(alpha = alpha), eyeRadius, Offset(center.x - eyeOffset, eyeY))
        drawCircle(FaceInk.copy(alpha = alpha), eyeRadius, Offset(center.x + eyeOffset, eyeY))
        if (radius >= 12f) {
            val shine = eyeRadius * 0.35f
            drawCircle(Color.White.copy(alpha = alpha), shine, Offset(center.x - eyeOffset - shine, eyeY - shine))
            drawCircle(Color.White.copy(alpha = alpha), shine, Offset(center.x + eyeOffset - shine, eyeY - shine))
        }
    }

    val mouthCenter = Offset(center.x, center.y + radius * 0.25f)
    when {
        anxious -> drawCircle(
            color = FaceInk.copy(alpha = alpha),
            radius = (radius * 0.095f).coerceAtLeast(1f),
            center = mouthCenter,
            style = Stroke(width = (radius * 0.045f).coerceAtLeast(1f)),
        )
        impact > 0.38f -> drawOval(
            color = FaceInk.copy(alpha = alpha),
            topLeft = Offset(mouthCenter.x - radius * 0.10f, mouthCenter.y - radius * 0.07f),
            size = Size(radius * 0.20f, radius * 0.18f),
        )
        style.face == FruitFace.CHEEKY -> {
            drawArc(
                color = FaceInk.copy(alpha = alpha),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - radius * 0.18f, mouthCenter.y - radius * 0.18f),
                size = Size(radius * 0.36f, radius * 0.30f),
                style = Stroke(width = (radius * 0.055f).coerceAtLeast(1f)),
            )
            drawCircle(
                color = style.blush.copy(alpha = alpha),
                radius = radius * 0.055f,
                center = mouthCenter + Offset(radius * 0.08f, radius * 0.10f),
            )
        }
        else -> drawArc(
            color = FaceInk.copy(alpha = alpha),
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(mouthCenter.x - radius * 0.18f, mouthCenter.y - radius * 0.18f),
            size = Size(radius * 0.36f, radius * 0.30f),
            style = Stroke(width = (radius * 0.055f).coerceAtLeast(1f)),
        )
    }
}

private fun DrawScope.drawFruitBody(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    fruitSize: Size,
    topLeft: Offset,
    style: FruitVisualSpec,
    alpha: Float,
) {
    drawOval(
        color = FaceInk.copy(alpha = 0.13f * alpha),
        topLeft = topLeft + Offset(radius * 0.08f, radius * 0.13f),
        size = fruitSize,
    )
    when (level) {
        FruitLevel.BLUEBERRY -> {
            drawCircle(style.base.copy(alpha = alpha), radius * 0.94f, center)
            drawCircle(style.highlight.copy(alpha = 0.35f * alpha), radius * 0.72f, center, style = Stroke(radius * 0.06f))
        }
        FruitLevel.CHERRY -> {
            val lobeRadius = radius * 0.72f
            drawCircle(style.base.copy(alpha = alpha), lobeRadius, center + Offset(-radius * 0.32f, radius * 0.08f))
            drawCircle(style.base.copy(alpha = alpha), lobeRadius, center + Offset(radius * 0.32f, radius * 0.08f))
            drawCircle(style.highlight.copy(alpha = 0.36f * alpha), radius * 0.22f, center + Offset(-radius * 0.50f, -radius * 0.26f))
        }
        FruitLevel.STRAWBERRY -> {
            drawOval(
                style.base.copy(alpha = alpha),
                topLeft = Offset(center.x - radius * 0.82f, center.y - radius * 0.92f),
                size = Size(radius * 1.64f, radius * 1.92f),
            )
            val seeds = arrayOf(
                Offset(-0.48f, -0.34f), Offset(0.48f, -0.34f),
                Offset(-0.56f, 0.18f), Offset(0.56f, 0.18f), Offset(0f, 0.54f),
            )
            for (seed in seeds) {
                drawOval(
                    StrawberrySeed.copy(alpha = 0.72f * alpha),
                    topLeft = center + Offset(seed.x * radius - radius * 0.045f, seed.y * radius - radius * 0.075f),
                    size = Size(radius * 0.09f, radius * 0.15f),
                )
            }
        }
        FruitLevel.PLUM -> {
            drawOval(style.base.copy(alpha = alpha), topLeft + Offset(radius * 0.08f, -radius * 0.02f), Size(fruitSize.width * 0.92f, fruitSize.height * 1.02f))
            drawArc(
                color = style.highlight.copy(alpha = 0.34f * alpha),
                startAngle = 95f,
                sweepAngle = 170f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.16f, center.y - radius * 0.74f),
                size = Size(radius * 0.55f, radius * 1.52f),
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
        }
        FruitLevel.MANDARIN -> {
            drawOval(style.base.copy(alpha = alpha), topLeft + Offset(0f, radius * 0.10f), Size(fruitSize.width, fruitSize.height * 0.90f))
            for (x in listOf(-0.42f, 0f, 0.42f)) {
                drawArc(
                    color = style.highlight.copy(alpha = 0.28f * alpha),
                    startAngle = 82f,
                    sweepAngle = 196f,
                    useCenter = false,
                    topLeft = Offset(center.x + x * radius - radius * 0.28f, center.y - radius * 0.74f),
                    size = Size(radius * 0.56f, radius * 1.48f),
                    style = Stroke((radius * 0.045f).coerceAtLeast(1f)),
                )
            }
        }
        FruitLevel.APPLE -> {
            drawCircle(style.base.copy(alpha = alpha), radius * 0.82f, center + Offset(-radius * 0.25f, radius * 0.08f))
            drawCircle(style.base.copy(alpha = alpha), radius * 0.82f, center + Offset(radius * 0.25f, radius * 0.08f))
        }
        FruitLevel.PEAR -> {
            drawOval(style.base.copy(alpha = alpha), Offset(center.x - radius * 0.86f, center.y - radius * 0.32f), Size(radius * 1.72f, radius * 1.38f))
            drawCircle(style.base.copy(alpha = alpha), radius * 0.56f, center + Offset(0f, -radius * 0.48f))
        }
        FruitLevel.PEACH -> {
            drawOval(style.base.copy(alpha = alpha), topLeft, fruitSize)
            drawArc(
                color = PeachSeam.copy(alpha = 0.48f * alpha),
                startAngle = 255f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.14f, center.y - radius * 0.82f),
                size = Size(radius * 0.64f, radius * 1.64f),
                style = Stroke((radius * 0.06f).coerceAtLeast(1f)),
            )
        }
        FruitLevel.PINEAPPLE -> {
            drawOval(style.base.copy(alpha = alpha), Offset(center.x - radius * 0.82f, center.y - radius * 0.91f), Size(radius * 1.64f, radius * 1.96f))
            for (offset in listOf(-0.48f, 0f, 0.48f)) {
                drawLine(
                    PineappleGrid.copy(alpha = 0.42f * alpha),
                    center + Offset(-radius * 0.62f, offset * radius),
                    center + Offset(radius * 0.62f, (offset + 0.42f) * radius),
                    strokeWidth = (radius * 0.045f).coerceAtLeast(1f),
                )
                drawLine(
                    PineappleGrid.copy(alpha = 0.42f * alpha),
                    center + Offset(radius * 0.62f, offset * radius),
                    center + Offset(-radius * 0.62f, (offset + 0.42f) * radius),
                    strokeWidth = (radius * 0.045f).coerceAtLeast(1f),
                )
            }
        }
        FruitLevel.MELON -> {
            drawCircle(style.base.copy(alpha = alpha), radius, center)
            for (x in listOf(-0.48f, 0f, 0.48f)) {
                drawArc(
                    MelonStripe.copy(alpha = 0.48f * alpha),
                    startAngle = 88f,
                    sweepAngle = 184f,
                    useCenter = false,
                    topLeft = Offset(center.x + x * radius - radius * 0.30f, center.y - radius * 0.86f),
                    size = Size(radius * 0.60f, radius * 1.72f),
                    style = Stroke((radius * 0.07f).coerceAtLeast(1f)),
                )
            }
        }
    }
}

private fun DrawScope.drawFruitTop(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    angleRadians: Float,
    alpha: Float,
) {
    val angleDegrees = angleRadians * (180f / PI.toFloat())
    rotate(degrees = angleDegrees, pivot = center) {
        when (level) {
            FruitLevel.BLUEBERRY -> repeat(5) { index ->
                drawCircle(
                    BlueberryCrown.copy(alpha = alpha),
                    radius * 0.14f,
                    center + Offset((index - 2) * radius * 0.13f, -radius * (0.82f + abs(index - 2) * 0.035f)),
                )
            }
            FruitLevel.CHERRY -> {
                drawLine(StemBrown.copy(alpha = alpha), center + Offset(-radius * 0.28f, -radius * 0.45f), center + Offset(0f, -radius * 1.12f), (radius * 0.08f).coerceAtLeast(1f))
                drawLine(StemBrown.copy(alpha = alpha), center + Offset(radius * 0.28f, -radius * 0.45f), center + Offset(0f, -radius * 1.12f), (radius * 0.08f).coerceAtLeast(1f))
            }
            FruitLevel.STRAWBERRY -> repeat(3) { index ->
                drawOval(
                    LeafGreen.copy(alpha = alpha),
                    Offset(center.x + (index - 1) * radius * 0.28f - radius * 0.24f, center.y - radius * 1.02f),
                    Size(radius * 0.48f, radius * 0.32f),
                )
            }
            FruitLevel.PINEAPPLE -> repeat(3) { index ->
                drawLine(
                    LeafGreen.copy(alpha = alpha),
                    center + Offset((index - 1) * radius * 0.18f, -radius * 0.72f),
                    center + Offset((index - 1) * radius * 0.36f, -radius * (1.38f - abs(index - 1) * 0.12f)),
                    (radius * 0.16f).coerceAtLeast(1f),
                )
            }
            FruitLevel.MELON -> Unit
            else -> {
                drawOval(
                    LeafGreen.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.04f, center.y - radius * 1.13f),
                    size = Size(radius * 0.64f, radius * 0.31f),
                )
                drawLine(
                    StemBrown.copy(alpha = alpha),
                    center + Offset(0f, -radius * 0.83f),
                    center + Offset(radius * 0.08f, -radius * 1.08f),
                    (radius * 0.10f).coerceAtLeast(1f),
                )
            }
        }
    }
}

private data class BoardTransform(val canvasSize: Size) {
    val side: Float = min(canvasSize.width, canvasSize.height).coerceAtLeast(1f)
    val origin: Offset = Offset((canvasSize.width - side) * 0.5f, (canvasSize.height - side) * 0.5f)

    fun world(x: Float, y: Float): Offset = Offset(origin.x + x * side, origin.y + y * side)
    fun worldY(y: Float): Float = origin.y + y * side
}

private const val PREVIEW_Y: Float = 0.08f
private const val CLEAR_TOUCH_MARGIN: Float = 0.035f
private val BoardCream = Color(0xFFFFF4DF)
private val BoardOutline = Color(0xFF9A765E)
private val DangerIdle = Color(0xFFC9937E)
private val DangerActive = Color(0xFFD84F4A)
private val ClearTarget = Color(0xFFCC5E43)
private val FaceInk = Color(0xFF49372F)
private val LeafGreen = Color(0xFF6E9A58)
private val StemBrown = Color(0xFF7D5B42)
private val BlueberryCrown = Color(0xFF3F477F)
private val StrawberrySeed = Color(0xFFFFD36A)
private val PeachSeam = Color(0xFFC96F78)
private val PineappleGrid = Color(0xFF9D7934)
private val MelonStripe = Color(0xFF3D8751)
