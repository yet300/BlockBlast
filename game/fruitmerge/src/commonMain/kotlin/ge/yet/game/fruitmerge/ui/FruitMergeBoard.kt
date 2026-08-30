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
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
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
    onMovePreview: (Float) -> Unit,
    onDrop: () -> Unit,
    onClearTarget: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dangerDash = remember { PathEffect.dashPathEffect(floatArrayOf(10f, 8f)) }
    val targetingClear = game.targetingMode == TargetingMode.CLEAR
    val latestBodies = rememberUpdatedState(game.bodies)
    val latestMovePreview = rememberUpdatedState(onMovePreview)
    val latestDrop = rememberUpdatedState(onDrop)
    val latestClearTarget = rememberUpdatedState(onClearTarget)
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .fruitMergePointerInput(
                enabled = game.phase == RunPhase.PLAYING,
                targetingClear = targetingClear,
                bodies = { latestBodies.value },
                onMovePreview = { latestMovePreview.value(it) },
                onDrop = { latestDrop.value() },
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

private fun Modifier.fruitMergePointerInput(
    enabled: Boolean,
    targetingClear: Boolean,
    bodies: () -> List<FruitBody>,
    onMovePreview: (Float) -> Unit,
    onDrop: () -> Unit,
    onClearTarget: (Long) -> Unit,
): Modifier = pointerInput(enabled, targetingClear) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!targetingClear) onMovePreview(worldX(down.position, size))
        var last: PointerInputChange = down
        var released = false
        while (last.pressed) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            last = change
            if (change.pressed && !targetingClear) {
                onMovePreview(worldX(change.position, size))
            } else if (!change.pressed) {
                released = true
            }
        }
        if (!released) return@awaitEachGesture
        if (targetingClear) {
            findClearTarget(last.position, size, bodies())?.let(onClearTarget)
        } else {
            onMovePreview(worldX(last.position, size))
            onDrop()
        }
    }
}

private fun worldX(position: Offset, size: IntSize): Float {
    val side = min(size.width, size.height).toFloat().coerceAtLeast(1f)
    val originX = (size.width - side) * 0.5f
    return ((position.x - originX) / side).coerceIn(0f, 1f)
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

private fun DrawScope.drawFruit(
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
    val style = fruitStyle(level)
    val squash = (abs(verticalVelocity) * 0.025f + impact * 0.10f).coerceIn(0f, 0.14f)
    val fruitSize = Size(radius * 2f * (1f + squash), radius * 2f * (1f - squash))
    val topLeft = Offset(center.x - fruitSize.width * 0.5f, center.y - fruitSize.height * 0.5f)

    drawOval(
        color = FaceInk.copy(alpha = 0.13f * alpha),
        topLeft = topLeft + Offset(radius * 0.08f, radius * 0.13f),
        size = fruitSize,
    )
    drawOval(color = style.base.copy(alpha = alpha), topLeft = topLeft, size = fruitSize)
    drawOval(
        color = style.highlight.copy(alpha = 0.72f * alpha),
        topLeft = Offset(center.x - radius * 0.54f, center.y - radius * 0.55f),
        size = Size(radius * 0.46f, radius * 0.30f),
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

    val angleDegrees = angleRadians * (180f / PI.toFloat())
    rotate(degrees = angleDegrees, pivot = center) {
        drawOval(
            color = LeafGreen.copy(alpha = alpha),
            topLeft = Offset(center.x + radius * 0.04f, center.y - radius * 1.13f),
            size = Size(radius * 0.64f, radius * 0.31f),
        )
        drawLine(
            color = StemBrown.copy(alpha = alpha),
            start = Offset(center.x, center.y - radius * 0.83f),
            end = Offset(center.x + radius * 0.08f, center.y - radius * 1.08f),
            strokeWidth = (radius * 0.10f).coerceAtLeast(1f),
        )
    }

    val blink = (facePhase % 4.2f) < 0.12f
    val eyeY = center.y - radius * 0.03f
    val eyeOffset = radius * 0.29f
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

private data class FruitStyle(
    val base: Color,
    val highlight: Color,
    val blush: Color,
)

private fun fruitStyle(level: FruitLevel): FruitStyle = FruitStyles[level.ordinal]

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
private val FruitStyles = listOf(
    FruitStyle(Color(0xFF6F78C9), Color(0xFFAEB6F2), Color(0xFFE6A1B2)),
    FruitStyle(Color(0xFFD95362), Color(0xFFFFA6A0), Color(0xFFF7A0AC)),
    FruitStyle(Color(0xFFE86761), Color(0xFFFFB6A7), Color(0xFFF79AA1)),
    FruitStyle(Color(0xFF8C64A7), Color(0xFFC9A6DD), Color(0xFFE4A2C0)),
    FruitStyle(Color(0xFFF29A45), Color(0xFFFFD09A), Color(0xFFF7A884)),
    FruitStyle(Color(0xFFD95D4F), Color(0xFFFFB09D), Color(0xFFF39494)),
    FruitStyle(Color(0xFFAEC65A), Color(0xFFDBE995), Color(0xFFE8A69A)),
    FruitStyle(Color(0xFFF29A82), Color(0xFFFFD0B9), Color(0xFFEFA0A6)),
    FruitStyle(Color(0xFFE7B94F), Color(0xFFFFDEA0), Color(0xFFE9A08C)),
    FruitStyle(Color(0xFF77B86A), Color(0xFFB9DF9D), Color(0xFFE39B98)),
)
