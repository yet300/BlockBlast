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
import androidx.compose.ui.graphics.drawscope.Stroke
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
    showPreview: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val palette = rememberFruitMergePalette()
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
            color = palette.woodDark.copy(alpha = 0.20f),
            topLeft = transform.origin + Offset(0f, transform.side * 0.018f),
            size = Size(transform.side, transform.side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        )
        drawRoundRect(
            color = palette.wood,
            topLeft = transform.origin,
            size = Size(transform.side, transform.side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
        )
        val crateLip = transform.side * 0.026f
        drawRoundRect(
            color = palette.boardCream,
            topLeft = transform.origin + Offset(crateLip, crateLip),
            size = Size(transform.side - crateLip * 2f, transform.side - crateLip * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius * 0.62f),
        )
        drawRoundRect(
            color = palette.woodDark.copy(alpha = 0.72f),
            topLeft = transform.origin,
            size = Size(transform.side, transform.side),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius),
            style = Stroke(width = transform.side * 0.010f),
        )
        drawRoundRect(
            color = palette.woodLight.copy(alpha = 0.72f),
            topLeft = transform.origin + Offset(crateLip, crateLip),
            size = Size(transform.side - crateLip * 2f, transform.side - crateLip * 2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius * 0.62f),
            style = Stroke(width = transform.side * 0.008f),
        )
        val jointRadius = transform.side * 0.008f
        listOf(
            Offset(0.055f, 0.055f),
            Offset(0.945f, 0.055f),
            Offset(0.055f, 0.945f),
            Offset(0.945f, 0.945f),
        ).forEach { joint ->
            drawCircle(
                color = palette.woodDark.copy(alpha = 0.54f),
                radius = jointRadius,
                center = transform.world(joint.x, joint.y),
            )
        }

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
            val guideOpacity = guideAlpha(game.dropCooldownSeconds)
            val dotStartY = previewCenter.y + game.previewLevel.radius * transform.side * 1.25f
            val dotEndY = transform.worldY(0.78f)
            repeat(GUIDE_DOT_COUNT) { index ->
                val progress = index / (GUIDE_DOT_COUNT - 1f)
                drawCircle(
                    color = GuideCoral.copy(alpha = (0.78f - progress * 0.38f) * guideOpacity),
                    radius = transform.side * (0.010f - progress * 0.003f),
                    center = Offset(previewCenter.x, dotStartY + (dotEndY - dotStartY) * progress),
                )
            }
            if (showPreview) {
                drawFruit(
                    level = game.previewLevel,
                    center = previewCenter,
                    radius = game.previewLevel.radius * transform.side,
                    angleRadians = 0f,
                    verticalVelocity = 0f,
                    impact = 0f,
                    facePhase = faceTimeSeconds + game.previewLevel.ordinal,
                    danger = DangerVisual(0f, false),
                    alpha = 1f,
                )
            }
        }

        for (body in game.bodies) {
            val danger = dangerVisual(
                topY = body.position.y - body.level.radius,
                dangerY = FruitMergeEngine.DANGER_Y,
                hasJoinedPile = body.hasJoinedPile,
            )
            drawFruit(
                level = body.level,
                center = transform.world(body.position.x, body.position.y),
                radius = body.level.radius * transform.side,
                angleRadians = body.angle,
                verticalVelocity = body.velocity.y,
                impact = body.impact,
                facePhase = if (reducedMotion) body.id.toFloat() else faceTimeSeconds + body.id * 0.37f,
                danger = danger,
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
            danger = DangerVisual(0f, false),
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

private data class BoardTransform(val canvasSize: Size) {
    val side: Float = min(canvasSize.width, canvasSize.height).coerceAtLeast(1f)
    val origin: Offset = Offset((canvasSize.width - side) * 0.5f, (canvasSize.height - side) * 0.5f)

    fun world(x: Float, y: Float): Offset = Offset(origin.x + x * side, origin.y + y * side)
    fun worldY(y: Float): Float = origin.y + y * side
}

internal fun fruitPreviewCenterInRoot(boardBoundsInRoot: androidx.compose.ui.geometry.Rect, previewX: Float): Offset {
    val side = min(boardBoundsInRoot.width, boardBoundsInRoot.height).coerceAtLeast(1f)
    val origin = Offset(
        x = boardBoundsInRoot.left + (boardBoundsInRoot.width - side) * 0.5f,
        y = boardBoundsInRoot.top + (boardBoundsInRoot.height - side) * 0.5f,
    )
    return Offset(
        x = origin.x + previewX.coerceIn(0f, 1f) * side,
        y = origin.y + PREVIEW_Y * side,
    )
}

private const val PREVIEW_Y: Float = 0.08f
private const val GUIDE_DOT_COUNT: Int = 11
private const val CLEAR_TOUCH_MARGIN: Float = 0.035f
private val DangerIdle = Color(0xFFC9937E)
private val DangerActive = Color(0xFFD84F4A)
private val GuideCoral = Color(0xFFE56C62)
private val ClearTarget = Color(0xFFCC5E43)
