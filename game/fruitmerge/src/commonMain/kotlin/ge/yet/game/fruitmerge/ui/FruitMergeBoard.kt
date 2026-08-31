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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
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

internal fun DrawScope.drawFruit(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    angleRadians: Float,
    verticalVelocity: Float,
    impact: Float,
    facePhase: Float,
    danger: DangerVisual,
    alpha: Float,
    merging: Boolean = false,
) {
    val style = fruitVisualSpec(level)
    val faceInk = style.faceInk
    val expression = fruitExpression(verticalVelocity, impact, danger, merging)
    val squash = (abs(verticalVelocity) * 0.025f + impact * 0.10f).coerceIn(0f, 0.14f)
    val fruitSize = Size(radius * 2f * (1f + squash), radius * 2f * (1f - squash))
    val topLeft = Offset(center.x - fruitSize.width * 0.5f, center.y - fruitSize.height * 0.5f)

    if (danger.intensity > 0f) {
        val pulse = 0.5f + sin(facePhase * 3.2f) * 0.5f
        drawCircle(
            color = DangerGlow.copy(alpha = (0.10f + pulse * 0.10f) * danger.intensity * alpha),
            radius = radius * (1.08f + pulse * 0.10f),
            center = center,
        )
        drawCircle(
            color = DangerActive.copy(alpha = 0.38f * danger.intensity * alpha),
            radius = radius * 1.04f,
            center = center,
            style = Stroke(width = (radius * 0.055f).coerceAtLeast(1f)),
        )
    }

    drawFruitBody(level, center, radius, fruitSize, topLeft, style, alpha)
    drawOval(
        color = Color.White.copy(alpha = 0.14f * alpha),
        topLeft = Offset(center.x - radius * 0.58f, center.y - radius * 0.58f),
        size = Size(radius * 0.42f, radius * 0.25f),
    )

    drawFruitTop(level, center, radius, angleRadians, alpha)

    val blink = (facePhase % 4.2f) < 0.12f
    val eyeY = center.y - radius * if (style.face == FruitFace.SLEEPY) 0.08f else 0.03f
    val eyeOffset = radius * if (style.face == FruitFace.SHY) 0.25f else 0.29f
    val eyeRadius = (radius * 0.075f).coerceAtLeast(1.15f)
    if (blink || expression == FruitExpression.IMPACT || expression == FruitExpression.MERGING) {
        val halfWidth = radius * 0.11f
        drawLine(
            color = faceInk.copy(alpha = alpha),
            start = Offset(center.x - eyeOffset - halfWidth, eyeY),
            end = Offset(center.x - eyeOffset + halfWidth, eyeY),
            strokeWidth = (radius * 0.055f).coerceAtLeast(1f),
        )
        drawLine(
            color = faceInk.copy(alpha = alpha),
            start = Offset(center.x + eyeOffset - halfWidth, eyeY),
            end = Offset(center.x + eyeOffset + halfWidth, eyeY),
            strokeWidth = (radius * 0.055f).coerceAtLeast(1f),
        )
    } else if (style.face == FruitFace.SLEEPY || style.face == FruitFace.SERENE) {
        val halfWidth = radius * 0.12f
        drawArc(
            color = faceInk.copy(alpha = alpha),
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(center.x - eyeOffset - halfWidth, eyeY - radius * 0.06f),
            size = Size(halfWidth * 2f, radius * 0.12f),
            style = Stroke(width = (radius * 0.05f).coerceAtLeast(1f)),
        )
        drawArc(
            color = faceInk.copy(alpha = alpha),
            startAngle = 18f,
            sweepAngle = 144f,
            useCenter = false,
            topLeft = Offset(center.x + eyeOffset - halfWidth, eyeY - radius * 0.06f),
            size = Size(halfWidth * 2f, radius * 0.12f),
            style = Stroke(width = (radius * 0.05f).coerceAtLeast(1f)),
        )
    } else {
        val leftEyeRadius = if (style.face == FruitFace.CHEEKY) eyeRadius * 0.72f else eyeRadius
        drawCircle(faceInk.copy(alpha = alpha), leftEyeRadius, Offset(center.x - eyeOffset, eyeY))
        drawCircle(faceInk.copy(alpha = alpha), eyeRadius, Offset(center.x + eyeOffset, eyeY))
        if (radius >= 12f && level.ordinal >= FruitLevel.MANDARIN.ordinal) {
            val shine = eyeRadius * 0.35f
            drawCircle(Color.White.copy(alpha = alpha), shine, Offset(center.x - eyeOffset - shine, eyeY - shine))
            drawCircle(Color.White.copy(alpha = alpha), shine, Offset(center.x + eyeOffset - shine, eyeY - shine))
        }
    }

    val mouthCenter = Offset(center.x, center.y + radius * 0.25f)
    when {
        expression == FruitExpression.CRYING -> drawCircle(
            color = faceInk.copy(alpha = alpha),
            radius = (radius * 0.095f).coerceAtLeast(1f),
            center = mouthCenter,
            style = Stroke(width = (radius * 0.045f).coerceAtLeast(1f)),
        )
        expression == FruitExpression.IMPACT || expression == FruitExpression.FALLING -> drawOval(
            color = faceInk.copy(alpha = alpha),
            topLeft = Offset(mouthCenter.x - radius * 0.10f, mouthCenter.y - radius * 0.07f),
            size = Size(radius * 0.20f, radius * 0.18f),
        )
        style.face == FruitFace.CHEEKY -> {
            drawArc(
                color = faceInk.copy(alpha = alpha),
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - radius * 0.18f, mouthCenter.y - radius * 0.18f),
                size = Size(radius * 0.36f, radius * 0.30f),
                style = Stroke(width = (radius * 0.055f).coerceAtLeast(1f)),
            )
            drawCircle(
                color = MarketCoral.copy(alpha = alpha),
                radius = radius * 0.055f,
                center = mouthCenter + Offset(radius * 0.08f, radius * 0.10f),
            )
        }
        expression == FruitExpression.MERGING || style.face == FruitFace.SHY || style.face == FruitFace.PROUD -> drawLine(
            color = faceInk.copy(alpha = alpha),
            start = Offset(mouthCenter.x - radius * 0.07f, mouthCenter.y),
            end = Offset(mouthCenter.x + radius * 0.07f, mouthCenter.y),
            strokeWidth = (radius * 0.045f).coerceAtLeast(1f),
            cap = StrokeCap.Round,
        )
        else -> Unit
    }

    if (danger.crying) {
        val tearTravel = (0.5f + sin(facePhase * 4.4f) * 0.5f) * radius * 0.22f
        val tearSize = Size(radius * 0.11f, radius * 0.22f)
        drawOval(
            color = TearBlue.copy(alpha = 0.86f * alpha),
            topLeft = Offset(center.x - eyeOffset - tearSize.width * 0.5f, eyeY + radius * 0.12f + tearTravel),
            size = tearSize,
        )
        drawOval(
            color = TearBlue.copy(alpha = 0.86f * alpha),
            topLeft = Offset(center.x + eyeOffset - tearSize.width * 0.5f, eyeY + radius * 0.18f + radius * 0.22f - tearTravel),
            size = tearSize,
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
    if (level.ordinal >= FruitLevel.MANDARIN.ordinal) {
        drawOval(
            color = style.shadow.copy(alpha = 0.28f * alpha),
            topLeft = topLeft + Offset(radius * 0.08f, radius * 0.13f),
            size = fruitSize,
        )
    }
    when (level) {
        FruitLevel.BLUEBERRY -> {
            drawCircle(style.shadow.copy(alpha = 0.34f * alpha), radius * 0.92f, center + Offset(radius * 0.06f, radius * 0.10f))
            drawCircle(style.base.copy(alpha = alpha), radius * 0.92f, center)
            drawCircle(
                style.outline.copy(alpha = 0.68f * alpha),
                radius * 0.92f,
                center,
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            drawCircle(
                BlueberryPowder.copy(alpha = 0.52f * alpha),
                radius * 0.74f,
                center,
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            drawOval(
                style.highlight.copy(alpha = 0.68f * alpha),
                Offset(center.x - radius * 0.52f, center.y - radius * 0.55f),
                Size(radius * 0.28f, radius * 0.18f),
            )
        }
        FruitLevel.RASPBERRY -> {
            for (offset in RaspberryDrupelets) {
                val drupeletCenter = center + Offset(offset.x * radius, offset.y * radius)
                drawCircle(
                    style.faceInk.copy(alpha = 0.10f * alpha),
                    radius * 0.34f,
                    drupeletCenter + Offset(radius * 0.035f, radius * 0.055f),
                )
                drawCircle(style.base.copy(alpha = alpha), radius * 0.32f, drupeletCenter)
                drawCircle(
                    style.outline.copy(alpha = 0.54f * alpha),
                    radius * 0.32f,
                    drupeletCenter,
                    style = Stroke((radius * 0.035f).coerceAtLeast(0.8f)),
                )
            }
            drawCircle(
                style.highlight.copy(alpha = 0.72f * alpha),
                radius * 0.09f,
                center + Offset(-radius * 0.42f, -radius * 0.42f),
            )
        }
        FruitLevel.STRAWBERRY -> {
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    drawPath(StrawberryShape, style.base.copy(alpha = alpha))
                    drawPath(
                        StrawberryShape,
                        style.outline.copy(alpha = 0.62f * alpha),
                        style = Stroke(0.055f),
                    )
                }
            }
            for (seed in StrawberrySeeds) {
                drawOval(
                    StrawberrySeed.copy(alpha = 0.86f * alpha),
                    topLeft = center + Offset(seed.x * radius - radius * 0.04f, seed.y * radius - radius * 0.065f),
                    size = Size(radius * 0.08f, radius * 0.13f),
                )
            }
            drawOval(
                style.highlight.copy(alpha = 0.62f * alpha),
                Offset(center.x - radius * 0.48f, center.y - radius * 0.48f),
                Size(radius * 0.22f, radius * 0.14f),
            )
        }
        FruitLevel.LIME -> {
            drawCircle(style.shadow.copy(alpha = 0.34f * alpha), radius * 0.94f, center + Offset(radius * 0.06f, radius * 0.10f))
            drawCircle(style.base.copy(alpha = alpha), radius * 0.94f, center)
            drawCircle(
                style.outline.copy(alpha = 0.62f * alpha),
                radius * 0.94f,
                center,
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            drawCircle(
                LimeRind.copy(alpha = 0.72f * alpha),
                radius * 0.82f,
                center,
                style = Stroke((radius * 0.075f).coerceAtLeast(1f)),
            )
            for (startAngle in LimeWedgeAngles) {
                drawArc(
                    color = style.highlight.copy(alpha = 0.46f * alpha),
                    startAngle = startAngle,
                    sweepAngle = 56f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.58f, center.y - radius * 0.58f),
                    size = Size(radius * 1.16f, radius * 1.16f),
                    style = Stroke((radius * 0.035f).coerceAtLeast(0.8f)),
                )
            }
            drawOval(
                style.highlight.copy(alpha = 0.66f * alpha),
                Offset(center.x - radius * 0.52f, center.y - radius * 0.55f),
                Size(radius * 0.24f, radius * 0.14f),
            )
        }
        FruitLevel.MANDARIN -> {
            drawOval(style.base.copy(alpha = alpha), topLeft + Offset(0f, radius * 0.10f), Size(fruitSize.width, fruitSize.height * 0.90f))
            drawOval(
                style.outline.copy(alpha = 0.58f * alpha),
                topLeft + Offset(0f, radius * 0.10f),
                Size(fruitSize.width, fruitSize.height * 0.90f),
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            for (index in -1..1) {
                val x = index * 0.42f
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
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    drawPath(
                        AppleShape,
                        style.outline.copy(alpha = 0.58f * alpha),
                        style = Stroke(0.055f),
                    )
                }
            }
        }
        FruitLevel.PEAR -> {
            drawOval(style.base.copy(alpha = alpha), Offset(center.x - radius * 0.86f, center.y - radius * 0.32f), Size(radius * 1.72f, radius * 1.38f))
            drawCircle(style.base.copy(alpha = alpha), radius * 0.56f, center + Offset(0f, -radius * 0.48f))
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    drawPath(
                        PearShape,
                        style.outline.copy(alpha = 0.54f * alpha),
                        style = Stroke(0.055f),
                    )
                }
            }
        }
        FruitLevel.PEACH -> {
            drawOval(style.base.copy(alpha = alpha), topLeft, fruitSize)
            drawOval(
                style.outline.copy(alpha = 0.52f * alpha),
                topLeft,
                fruitSize,
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
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
            drawOval(
                style.outline.copy(alpha = 0.58f * alpha),
                Offset(center.x - radius * 0.82f, center.y - radius * 0.91f),
                Size(radius * 1.64f, radius * 1.96f),
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            for (index in -1..1) {
                val offset = index * 0.48f
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
        FruitLevel.WATERMELON -> {
            drawCircle(style.base.copy(alpha = alpha), radius, center)
            drawCircle(
                style.outline.copy(alpha = 0.58f * alpha),
                radius,
                center,
                style = Stroke((radius * 0.055f).coerceAtLeast(1f)),
            )
            for (index in -1..1) {
                val x = index * 0.48f
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

private val StrawberryShape = Path().apply {
    moveTo(-0.66f, -0.64f)
    cubicTo(-0.88f, -0.36f, -0.80f, 0.18f, -0.46f, 0.58f)
    cubicTo(-0.26f, 0.80f, -0.08f, 0.94f, 0.02f, 1.00f)
    cubicTo(0.18f, 0.88f, 0.40f, 0.70f, 0.58f, 0.43f)
    cubicTo(0.84f, 0.04f, 0.86f, -0.42f, 0.62f, -0.67f)
    cubicTo(0.34f, -0.88f, -0.38f, -0.88f, -0.66f, -0.64f)
    close()
}

private val AppleShape = Path().apply {
    moveTo(0f, -0.66f)
    cubicTo(-0.28f, -0.88f, -0.88f, -0.72f, -0.94f, -0.06f)
    cubicTo(-1.00f, 0.62f, -0.48f, 0.92f, 0f, 0.86f)
    cubicTo(0.48f, 0.92f, 1.00f, 0.62f, 0.94f, -0.06f)
    cubicTo(0.88f, -0.72f, 0.28f, -0.88f, 0f, -0.66f)
    close()
}

private val PearShape = Path().apply {
    moveTo(0f, -1.02f)
    cubicTo(-0.48f, -0.88f, -0.44f, -0.44f, -0.72f, -0.20f)
    cubicTo(-1.08f, 0.12f, -0.92f, 0.86f, 0f, 0.92f)
    cubicTo(0.92f, 0.86f, 1.08f, 0.12f, 0.72f, -0.20f)
    cubicTo(0.44f, -0.44f, 0.48f, -0.88f, 0f, -1.02f)
    close()
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
            FruitLevel.BLUEBERRY -> {
                val crownCenter = center + Offset(0f, -radius * 0.76f)
                repeat(5) { index ->
                    rotate(index * 72f, crownCenter) {
                        drawOval(
                            BlueberryCrown.copy(alpha = alpha),
                            Offset(crownCenter.x - radius * 0.10f, crownCenter.y - radius * 0.28f),
                            Size(radius * 0.20f, radius * 0.34f),
                        )
                    }
                }
                drawCircle(BlueberryCrown.copy(alpha = alpha), radius * 0.10f, crownCenter)
            }
            FruitLevel.RASPBERRY -> {
                repeat(3) { index ->
                    rotate((index - 1) * 34f, center + Offset(0f, -radius * 0.64f)) {
                        drawOval(
                            RaspberryLeaf.copy(alpha = alpha),
                            Offset(center.x - radius * 0.23f, center.y - radius * 0.94f),
                            Size(radius * 0.46f, radius * 0.34f),
                        )
                    }
                }
            }
            FruitLevel.STRAWBERRY -> repeat(3) { index ->
                drawOval(
                    LeafGreen.copy(alpha = alpha),
                    Offset(center.x + (index - 1) * radius * 0.28f - radius * 0.24f, center.y - radius * 1.02f),
                    Size(radius * 0.48f, radius * 0.32f),
                )
            }
            FruitLevel.LIME -> {
                drawOval(
                    LimeLeaf.copy(alpha = alpha),
                    Offset(center.x + radius * 0.02f, center.y - radius * 1.02f),
                    Size(radius * 0.58f, radius * 0.28f),
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
            FruitLevel.WATERMELON -> Unit
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
private val DangerGlow = Color(0xFFFF8A65)
private val GuideCoral = Color(0xFFE56C62)
private val ClearTarget = Color(0xFFCC5E43)
private val TearBlue = Color(0xFF75C9E8)
private val LeafGreen = Color(0xFF6E9A58)
private val StemBrown = Color(0xFF7D5B42)
private val BlueberryCrown = Color(0xFF3F477F)
private val BlueberryPowder = Color(0xFF9EB0E5)
private val RaspberryLeaf = Color(0xFF527B46)
private val StrawberrySeed = Color(0xFFFFD36A)
private val LimeRind = Color(0xFF3D873D)
private val LimeLeaf = Color(0xFF4F8F43)
private val PeachSeam = Color(0xFFC96F78)
private val PineappleGrid = Color(0xFF9D7934)
private val MelonStripe = Color(0xFF3D8751)

private val RaspberryDrupelets = listOf(
    Offset(0f, -0.54f),
    Offset(-0.34f, -0.32f),
    Offset(0.34f, -0.32f),
    Offset(-0.51f, 0.02f),
    Offset(0f, 0f),
    Offset(0.51f, 0.02f),
    Offset(-0.31f, 0.38f),
    Offset(0.31f, 0.38f),
    Offset(0f, 0.63f),
)
private val StrawberrySeeds = listOf(
    Offset(-0.38f, -0.32f),
    Offset(0.28f, -0.38f),
    Offset(-0.52f, 0.06f),
    Offset(0.46f, 0.02f),
    Offset(-0.20f, 0.30f),
    Offset(0.23f, 0.42f),
    Offset(0.02f, 0.68f),
)
private val LimeWedgeAngles = listOf(18f, 138f, 258f)
