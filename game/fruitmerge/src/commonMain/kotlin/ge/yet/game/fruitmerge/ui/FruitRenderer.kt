package ge.yet.game.fruitmerge.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import ge.yet.game.fruitmerge.engine.FruitLevel
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * High-performance Compose Multiplatform Canvas renderer for "Squishy Fruit Characters".
 *
 * Renders soft, volumetric, collectible vinyl/squishy toy fruits with unique personalities,
 * soft volume gradients, glossy highlights, gentle outlines, and dynamic facial expressions.
 */
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
    if (radius <= 0f || alpha <= 0f) return

    val spec = fruitVisualSpec(level)
    val expression = fruitExpression(verticalVelocity, impact, danger, merging)

    // Squishy physics deformation
    val squash = (abs(verticalVelocity) * 0.024f + impact * 0.11f).coerceIn(0f, 0.14f)
    val fruitWidth = radius * 2f * (1f + squash)
    val fruitHeight = radius * 2f * (1f - squash)
    val fruitSize = Size(fruitWidth, fruitHeight)
    val topLeft = Offset(center.x - fruitWidth * 0.5f, center.y - fruitHeight * 0.5f)

    // 1. Danger Warning Aura (when approaching or crossing the danger line)
    if (danger.intensity > 0f) {
        val pulse = 0.5f + sin(facePhase * 3.6f) * 0.5f
        drawCircle(
            color = DangerGlow.copy(alpha = (0.12f + pulse * 0.12f) * danger.intensity * alpha),
            radius = radius * (1.10f + pulse * 0.10f),
            center = center,
        )
        drawCircle(
            color = DangerActive.copy(alpha = 0.40f * danger.intensity * alpha),
            radius = radius * 1.05f,
            center = center,
            style = Stroke(width = (radius * 0.055f).coerceAtLeast(1.2f)),
        )
    }

    val angleDegrees = angleRadians * (180f / PI.toFloat())
    rotate(degrees = angleDegrees, pivot = center) {
        // 2. Fruit Body: Volume Gradient, Silhouette, Details & Glossy Highlights
        drawFruitSquishyBody(
            level = level,
            center = center,
            radius = radius,
            fruitSize = fruitSize,
            topLeft = topLeft,
            spec = spec,
            alpha = alpha,
        )

        // 3. Stems, Leaves, Calyx Crowns & Vines
        drawFruitCrownAndStem(
            level = level,
            center = center,
            radius = radius,
            spec = spec,
            alpha = alpha,
        )

        // 4. Character Faces with Emotional Expressions & Dynamic Reactions
        drawFruitFace(
            level = level,
            center = center,
            radius = radius,
            spec = spec,
            expression = expression,
            facePhase = facePhase,
            danger = danger,
            alpha = alpha,
        )
    }
}

/**
 * Draws the squishy fruit body, volume depth, surface details, and glossy specular highlights.
 */
private fun DrawScope.drawFruitSquishyBody(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    fruitSize: Size,
    topLeft: Offset,
    spec: FruitVisualSpec,
    alpha: Float,
) {
    val outlineStroke = (radius * 0.052f).coerceAtLeast(1.1f)
    val outlineColor = spec.outline.copy(alpha = 0.60f * alpha)

    // Soft Ambient Bottom Shadow
    if (level.ordinal >= FruitLevel.MANDARIN.ordinal) {
        drawOval(
            color = spec.shadow.copy(alpha = 0.26f * alpha),
            topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.65f + radius * 0.22f),
            size = Size(radius * 1.76f, radius * 1.50f),
        )
    }

    when (level) {
        FruitLevel.BLUEBERRY -> {
            // Volume shadow base
            drawCircle(
                color = spec.shadow.copy(alpha = 0.38f * alpha),
                radius = radius * 0.94f,
                center = center + Offset(radius * 0.06f, radius * 0.08f),
            )
            // Main plump berry body
            drawCircle(color = spec.base.copy(alpha = alpha), radius = radius * 0.94f, center = center)
            // Upper glow
            drawCircle(
                color = spec.highlight.copy(alpha = 0.25f * alpha),
                radius = radius * 0.65f,
                center = center + Offset(-radius * 0.16f, -radius * 0.18f),
            )
            // Blueberry soft powder bloom ring
            drawCircle(
                color = spec.accent.copy(alpha = 0.45f * alpha),
                radius = radius * 0.72f,
                center = center,
                style = Stroke(width = (radius * 0.065f).coerceAtLeast(1f)),
            )
            // Outline
            drawCircle(color = outlineColor, radius = radius * 0.94f, center = center, style = Stroke(outlineStroke))
            // Glossy Specular Pill Highlight
            drawGlossPill(center, radius, alpha)
        }

        FruitLevel.RASPBERRY -> {
            // Plump Drupelet Cluster
            for (offset in RaspberryDrupeletOffsets) {
                val drupeCenter = center + Offset(offset.x * radius, offset.y * radius)
                val drupeRadius = radius * 0.33f
                // Drupelet shadow
                drawCircle(
                    color = spec.shadow.copy(alpha = 0.42f * alpha),
                    radius = drupeRadius,
                    center = drupeCenter + Offset(radius * 0.035f, radius * 0.045f),
                )
                // Drupelet base
                drawCircle(color = spec.base.copy(alpha = alpha), radius = drupeRadius, center = drupeCenter)
                // Drupelet top glow
                drawCircle(
                    color = spec.highlight.copy(alpha = 0.40f * alpha),
                    radius = drupeRadius * 0.58f,
                    center = drupeCenter + Offset(-drupeRadius * 0.24f, -drupeRadius * 0.24f),
                )
                // Drupelet outline
                drawCircle(
                    color = outlineColor,
                    radius = drupeRadius,
                    center = drupeCenter,
                    style = Stroke((radius * 0.034f).coerceAtLeast(0.8f)),
                )
                // Micro gloss on top drupelets
                if (offset.y < 0.1f) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.55f * alpha),
                        radius = drupeRadius * 0.22f,
                        center = drupeCenter + Offset(-drupeRadius * 0.32f, -drupeRadius * 0.32f),
                    )
                }
            }
            // Master highlight on cluster
            drawCircle(
                color = Color.White.copy(alpha = 0.40f * alpha),
                radius = radius * 0.12f,
                center = center + Offset(-radius * 0.38f, -radius * 0.38f),
            )
        }

        FruitLevel.STRAWBERRY -> {
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    // Base silhouette
                    drawPath(StrawberryShape, spec.base.copy(alpha = alpha))
                    // Upper glow
                    drawPath(StrawberryShape, spec.highlight.copy(alpha = 0.22f * alpha))
                    // Contour outline
                    drawPath(StrawberryShape, outlineColor, style = Stroke(0.052f))
                }
            }
            // Decorative golden seed specks
            for (seed in StrawberrySeedOffsets) {
                val seedPos = center + Offset(seed.x * radius, seed.y * radius)
                val seedWidth = radius * 0.075f
                val seedHeight = radius * 0.12f
                // Seed shadow
                drawOval(
                    color = spec.shadow.copy(alpha = 0.45f * alpha),
                    topLeft = seedPos - Offset(seedWidth * 0.5f - radius * 0.015f, seedHeight * 0.5f - radius * 0.015f),
                    size = Size(seedWidth, seedHeight),
                )
                // Seed golden bead
                drawOval(
                    color = spec.accent.copy(alpha = 0.90f * alpha),
                    topLeft = seedPos - Offset(seedWidth * 0.5f, seedHeight * 0.5f),
                    size = Size(seedWidth, seedHeight),
                )
            }
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -30f, offset = Offset(-radius * 0.38f, -radius * 0.44f))
        }

        FruitLevel.LIME -> {
            // Shadow sphere
            drawCircle(
                color = spec.shadow.copy(alpha = 0.36f * alpha),
                radius = radius * 0.95f,
                center = center + Offset(radius * 0.06f, radius * 0.08f),
            )
            // Base sphere
            drawCircle(color = spec.base.copy(alpha = alpha), radius = radius * 0.95f, center = center)
            // Upper glow
            drawCircle(
                color = spec.highlight.copy(alpha = 0.30f * alpha),
                radius = radius * 0.68f,
                center = center + Offset(-radius * 0.18f, -radius * 0.18f),
            )
            // Inner citrus rind ring
            drawCircle(
                color = spec.accent.copy(alpha = 0.42f * alpha),
                radius = radius * 0.84f,
                center = center,
                style = Stroke(width = (radius * 0.065f).coerceAtLeast(1f)),
            )
            // Slice wedge segment accents
            for (startAngle in LimeWedgeAngles) {
                drawArc(
                    color = spec.highlight.copy(alpha = 0.48f * alpha),
                    startAngle = startAngle,
                    sweepAngle = 54f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.60f, center.y - radius * 0.60f),
                    size = Size(radius * 1.20f, radius * 1.20f),
                    style = Stroke(width = (radius * 0.040f).coerceAtLeast(0.9f), cap = StrokeCap.Round),
                )
            }
            // Outline
            drawCircle(color = outlineColor, radius = radius * 0.95f, center = center, style = Stroke(outlineStroke))
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha)
        }

        FruitLevel.MANDARIN -> {
            val ovalBounds = topLeft + Offset(0f, radius * 0.08f)
            val ovalSize = Size(fruitSize.width, fruitSize.height * 0.92f)
            // Base oval
            drawOval(color = spec.base.copy(alpha = alpha), topLeft = ovalBounds, size = ovalSize)
            // Upper soft glow
            drawOval(
                color = spec.highlight.copy(alpha = 0.28f * alpha),
                topLeft = Offset(center.x - radius * 0.70f, center.y - radius * 0.70f),
                size = Size(radius * 1.25f, radius * 0.90f),
            )
            // Segment crease curves
            for (index in -1..1) {
                val xOffset = index * 0.44f * radius
                drawArc(
                    color = spec.accent.copy(alpha = 0.32f * alpha),
                    startAngle = 78f,
                    sweepAngle = 204f,
                    useCenter = false,
                    topLeft = Offset(center.x + xOffset - radius * 0.28f, center.y - radius * 0.74f),
                    size = Size(radius * 0.56f, radius * 1.46f),
                    style = Stroke(width = (radius * 0.045f).coerceAtLeast(1f), cap = StrokeCap.Round),
                )
            }
            // Outline
            drawOval(color = outlineColor, topLeft = ovalBounds, size = ovalSize, style = Stroke(outlineStroke))
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, offset = Offset(-radius * 0.36f, -radius * 0.44f))
        }

        FruitLevel.APPLE -> {
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    // Base cardioid body
                    drawPath(AppleShape, spec.base.copy(alpha = alpha))
                    // Upper gloss glow
                    drawPath(AppleShape, spec.highlight.copy(alpha = 0.24f * alpha))
                    // Outline
                    drawPath(AppleShape, outlineColor, style = Stroke(0.052f))
                }
            }
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -28f, offset = Offset(-radius * 0.38f, -radius * 0.46f))
        }

        FruitLevel.PEAR -> {
            translate(center.x, center.y) {
                scale(radius, radius, Offset.Zero) {
                    // Base pear body
                    drawPath(PearShape, spec.base.copy(alpha = alpha))
                    // Upper glow
                    drawPath(PearShape, spec.highlight.copy(alpha = 0.22f * alpha))
                    // Outline
                    drawPath(PearShape, outlineColor, style = Stroke(0.052f))
                }
            }
            // Golden belly blush
            drawOval(
                color = spec.blush.copy(alpha = 0.35f * alpha),
                topLeft = Offset(center.x - radius * 0.45f, center.y + radius * 0.12f),
                size = Size(radius * 0.90f, radius * 0.60f),
            )
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -20f, offset = Offset(-radius * 0.28f, -radius * 0.58f))
        }

        FruitLevel.PEACH -> {
            // Base peach body
            drawOval(color = spec.base.copy(alpha = alpha), topLeft = topLeft, size = fruitSize)
            // Upper pearl glow
            drawOval(
                color = spec.highlight.copy(alpha = 0.32f * alpha),
                topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
                size = Size(radius * 1.25f, radius * 1.05f),
            )
            // Elegant vertical cleft seam line
            drawArc(
                color = spec.accent.copy(alpha = 0.52f * alpha),
                startAngle = 260f,
                sweepAngle = 145f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.12f, center.y - radius * 0.88f),
                size = Size(radius * 0.58f, radius * 1.74f),
                style = Stroke(width = (radius * 0.058f).coerceAtLeast(1.2f), cap = StrokeCap.Round),
            )
            // Outline
            drawOval(color = outlineColor, topLeft = topLeft, size = fruitSize, style = Stroke(outlineStroke))
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -25f, offset = Offset(-radius * 0.38f, -radius * 0.44f))
        }

        FruitLevel.PINEAPPLE -> {
            val barrelBounds = Offset(center.x - radius * 0.84f, center.y - radius * 0.90f)
            val barrelSize = Size(radius * 1.68f, radius * 1.94f)
            val barrelRadius = CornerRadius(radius * 0.44f, radius * 0.44f)

            // Base barrel
            drawRoundRect(color = spec.base.copy(alpha = alpha), topLeft = barrelBounds, size = barrelSize, cornerRadius = barrelRadius)
            // Upper glow
            drawRoundRect(
                color = spec.highlight.copy(alpha = 0.26f * alpha),
                topLeft = barrelBounds,
                size = Size(barrelSize.width, barrelSize.height * 0.65f),
                cornerRadius = barrelRadius,
            )
            // Geometric diamond scale pattern
            for (index in -1..1) {
                val offset = index * 0.46f * radius
                drawLine(
                    color = spec.accent.copy(alpha = 0.46f * alpha),
                    start = center + Offset(-radius * 0.62f, offset),
                    end = center + Offset(radius * 0.62f, offset + 0.46f * radius),
                    strokeWidth = (radius * 0.045f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = spec.accent.copy(alpha = 0.46f * alpha),
                    start = center + Offset(radius * 0.62f, offset),
                    end = center + Offset(-radius * 0.62f, offset + 0.46f * radius),
                    strokeWidth = (radius * 0.045f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
            }
            // Diamond center scale nodes
            for (node in PineappleScaleNodes) {
                drawCircle(
                    color = spec.highlight.copy(alpha = 0.55f * alpha),
                    radius = radius * 0.055f,
                    center = center + Offset(node.x * radius, node.y * radius),
                )
            }
            // Outline
            drawRoundRect(color = outlineColor, topLeft = barrelBounds, size = barrelSize, cornerRadius = barrelRadius, style = Stroke(outlineStroke))
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -15f, offset = Offset(-radius * 0.35f, -radius * 0.54f))
        }

        FruitLevel.WATERMELON -> {
            // Base sphere
            drawCircle(color = spec.base.copy(alpha = alpha), radius = radius, center = center)
            // Lower sunlit belly
            drawOval(
                color = spec.highlight.copy(alpha = 0.24f * alpha),
                topLeft = Offset(center.x - radius * 0.70f, center.y + radius * 0.20f),
                size = Size(radius * 1.40f, radius * 0.70f),
            )
            // Wavy dark emerald melon stripes
            for (index in -2..2) {
                val xPos = index * 0.40f * radius
                drawArc(
                    color = spec.accent.copy(alpha = 0.68f * alpha),
                    startAngle = 84f,
                    sweepAngle = 192f,
                    useCenter = false,
                    topLeft = Offset(center.x + xPos - radius * 0.26f, center.y - radius * 0.90f),
                    size = Size(radius * 0.52f, radius * 1.80f),
                    style = Stroke(width = (radius * 0.082f).coerceAtLeast(1.5f), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            // Outline
            drawCircle(color = outlineColor, radius = radius, center = center, style = Stroke(outlineStroke))
            // Glossy Specular Pill
            drawGlossPill(center, radius, alpha, angle = -32f, offset = Offset(-radius * 0.42f, -radius * 0.48f))
        }
    }
}

/**
 * Draws soft glossy specular pill & secondary micro dot highlights for squishy toy volume.
 */
private fun DrawScope.drawGlossPill(
    center: Offset,
    radius: Float,
    alpha: Float,
    angle: Float = -35f,
    offset: Offset = Offset(-radius * 0.40f, -radius * 0.48f),
) {
    val pillCenter = center + offset
    val pillWidth = radius * 0.32f
    val pillHeight = radius * 0.18f

    rotate(degrees = angle, pivot = pillCenter) {
        drawOval(
            color = Color.White.copy(alpha = 0.62f * alpha),
            topLeft = Offset(pillCenter.x - pillWidth * 0.5f, pillCenter.y - pillHeight * 0.5f),
            size = Size(pillWidth, pillHeight),
        )
    }
    // Secondary micro shine dot
    drawCircle(
        color = Color.White.copy(alpha = 0.52f * alpha),
        radius = radius * 0.050f,
        center = pillCenter + Offset(radius * 0.22f, -radius * 0.08f),
    )
}

/**
 * Draws character faces with distinct personalities and dynamic emotional states.
 */
private fun DrawScope.drawFruitFace(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    spec: FruitVisualSpec,
    expression: FruitExpression,
    facePhase: Float,
    danger: DangerVisual,
    alpha: Float,
) {
    val faceInk = spec.faceInk.copy(alpha = alpha)
    val blushColor = spec.blush.copy(alpha = 0.55f * alpha)
    val strokeWidth = (radius * 0.052f).coerceAtLeast(1.1f)

    // Eye placement parameters
    val eyeY = center.y - radius * if (spec.face == FruitFace.SLEEPY) 0.06f else 0.02f
    val eyeSpacing = radius * if (spec.face == FruitFace.CURIOUS || level == FruitLevel.PEAR) 0.33f else 0.28f
    val eyeRadius = (radius * 0.078f).coerceAtLeast(1.3f)
    val leftEyeCenter = Offset(center.x - eyeSpacing, eyeY)
    val rightEyeCenter = Offset(center.x + eyeSpacing, eyeY)

    // Blushing Cheeks (cute round soft blush pads)
    val blushY = eyeY + radius * 0.14f
    val blushRadius = (radius * 0.12f).coerceAtLeast(1.8f)
    drawOval(
        color = blushColor,
        topLeft = Offset(leftEyeCenter.x - blushRadius * 0.9f, blushY - blushRadius * 0.5f),
        size = Size(blushRadius * 1.8f, blushRadius * 1.1f),
    )
    drawOval(
        color = blushColor,
        topLeft = Offset(rightEyeCenter.x - blushRadius * 0.9f, blushY - blushRadius * 0.5f),
        size = Size(blushRadius * 1.8f, blushRadius * 1.1f),
    )

    // Dynamic Blinking check
    val isBlinking = (facePhase % 4.2f) < 0.14f

    // Draw Eyes
    if (isBlinking || expression == FruitExpression.IMPACT || expression == FruitExpression.MERGING) {
        // Squint / squeeze eyes (> < or closed horizontal arcs)
        val halfW = radius * 0.11f
        if (expression == FruitExpression.IMPACT || expression == FruitExpression.MERGING) {
            // Sparkly squeeze (> <)
            drawLine(faceInk, Offset(leftEyeCenter.x - halfW, eyeY - halfW * 0.7f), Offset(leftEyeCenter.x + halfW * 0.6f, eyeY), strokeWidth, StrokeCap.Round)
            drawLine(faceInk, Offset(leftEyeCenter.x - halfW, eyeY + halfW * 0.7f), Offset(leftEyeCenter.x + halfW * 0.6f, eyeY), strokeWidth, StrokeCap.Round)
            drawLine(faceInk, Offset(rightEyeCenter.x + halfW, eyeY - halfW * 0.7f), Offset(rightEyeCenter.x - halfW * 0.6f, eyeY), strokeWidth, StrokeCap.Round)
            drawLine(faceInk, Offset(rightEyeCenter.x + halfW, eyeY + halfW * 0.7f), Offset(rightEyeCenter.x - halfW * 0.6f, eyeY), strokeWidth, StrokeCap.Round)
        } else {
            // Calm closed sleeping/blinking line
            drawLine(faceInk, Offset(leftEyeCenter.x - halfW, eyeY), Offset(leftEyeCenter.x + halfW, eyeY), strokeWidth, StrokeCap.Round)
            drawLine(faceInk, Offset(rightEyeCenter.x - halfW, eyeY), Offset(rightEyeCenter.x + halfW, eyeY), strokeWidth, StrokeCap.Round)
        }
    } else when (spec.face) {
        FruitFace.SLEEPY, FruitFace.SERENE, FruitFace.GENTLE -> {
            // Calm happy closed curved smiling eyes ( ◡  ◡ )
            val halfW = radius * 0.11f
            drawArc(
                color = faceInk,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(leftEyeCenter.x - halfW, eyeY - radius * 0.05f),
                size = Size(halfW * 2f, radius * 0.10f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = faceInk,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(rightEyeCenter.x - halfW, eyeY - radius * 0.05f),
                size = Size(halfW * 2f, radius * 0.10f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        FruitFace.CHEEKY -> {
            // Mischievous Raspberry: Wink on left eye, glossy circle on right!
            val halfW = radius * 0.10f
            // Left eye: Wink ( ^ )
            drawLine(faceInk, Offset(leftEyeCenter.x - halfW, eyeY + halfW * 0.4f), Offset(leftEyeCenter.x, eyeY - halfW * 0.5f), strokeWidth, StrokeCap.Round)
            drawLine(faceInk, Offset(leftEyeCenter.x, eyeY - halfW * 0.5f), Offset(leftEyeCenter.x + halfW, eyeY + halfW * 0.4f), strokeWidth, StrokeCap.Round)
            // Right eye: Big open shiny eye
            drawCircle(faceInk, eyeRadius, rightEyeCenter)
            drawCircle(Color.White.copy(alpha = alpha), eyeRadius * 0.38f, rightEyeCenter - Offset(eyeRadius * 0.28f, eyeRadius * 0.28f))
        }

        FruitFace.CURIOUS -> {
            // Curious Pear: Wide derpy shiny eyes
            drawCircle(faceInk, eyeRadius * 1.15f, leftEyeCenter)
            drawCircle(faceInk, eyeRadius * 1.15f, rightEyeCenter)
            val shineR = eyeRadius * 0.42f
            drawCircle(Color.White.copy(alpha = alpha), shineR, leftEyeCenter - Offset(shineR * 0.5f, shineR * 0.5f))
            drawCircle(Color.White.copy(alpha = alpha), shineR, rightEyeCenter - Offset(shineR * 0.5f, shineR * 0.5f))
            // Secondary micro sparkle
            drawCircle(Color.White.copy(alpha = alpha), shineR * 0.45f, leftEyeCenter + Offset(shineR * 0.5f, shineR * 0.4f))
            drawCircle(Color.White.copy(alpha = alpha), shineR * 0.45f, rightEyeCenter + Offset(shineR * 0.5f, shineR * 0.4f))
        }

        else -> {
            // Bright, Sunny, Proud, Bold, Shy: Big sparkling anime-style open eyes
            drawCircle(faceInk, eyeRadius, leftEyeCenter)
            drawCircle(faceInk, eyeRadius, rightEyeCenter)
            val shineR = eyeRadius * 0.38f
            drawCircle(Color.White.copy(alpha = alpha), shineR, leftEyeCenter - Offset(shineR * 0.5f, shineR * 0.5f))
            drawCircle(Color.White.copy(alpha = alpha), shineR, rightEyeCenter - Offset(shineR * 0.5f, shineR * 0.5f))
            if (radius >= 14f) {
                // Secondary lower reflection
                drawCircle(Color.White.copy(alpha = 0.70f * alpha), shineR * 0.40f, leftEyeCenter + Offset(shineR * 0.4f, shineR * 0.4f))
                drawCircle(Color.White.copy(alpha = 0.70f * alpha), shineR * 0.40f, rightEyeCenter + Offset(shineR * 0.4f, shineR * 0.4f))
            }
        }
    }

    // Mouth Expression
    val mouthCenter = Offset(center.x, center.y + radius * 0.22f)
    when {
        expression == FruitExpression.CRYING -> {
            // Worried open crying mouth
            drawOval(
                color = faceInk,
                topLeft = Offset(mouthCenter.x - radius * 0.12f, mouthCenter.y - radius * 0.08f),
                size = Size(radius * 0.24f, radius * 0.18f),
                style = Stroke(width = strokeWidth),
            )
        }

        expression == FruitExpression.FALLING -> {
            // Surprised round 'o' mouth
            drawOval(
                color = faceInk,
                topLeft = Offset(mouthCenter.x - radius * 0.08f, mouthCenter.y - radius * 0.08f),
                size = Size(radius * 0.16f, radius * 0.18f),
            )
        }

        expression == FruitExpression.IMPACT || expression == FruitExpression.MERGING -> {
            // Wide beaming merge grin ( D )
            drawArc(
                color = faceInk,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(mouthCenter.x - radius * 0.16f, mouthCenter.y - radius * 0.06f),
                size = Size(radius * 0.32f, radius * 0.22f),
            )
        }

        spec.face == FruitFace.BRIGHT -> {
            // Cheerful open happy laughing mouth
            drawArc(
                color = faceInk,
                startAngle = 10f,
                sweepAngle = 160f,
                useCenter = true,
                topLeft = Offset(mouthCenter.x - radius * 0.14f, mouthCenter.y - radius * 0.04f),
                size = Size(radius * 0.28f, radius * 0.20f),
            )
        }

        spec.face == FruitFace.CHEEKY -> {
            // Mischievous side-smirk with cute tongue peek
            drawArc(
                color = faceInk,
                startAngle = 15f,
                sweepAngle = 150f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - radius * 0.14f, mouthCenter.y - radius * 0.12f),
                size = Size(radius * 0.28f, radius * 0.24f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawCircle(
                color = spec.blush.copy(alpha = alpha),
                radius = radius * 0.050f,
                center = mouthCenter + Offset(radius * 0.06f, radius * 0.08f),
            )
        }

        spec.face == FruitFace.SUNNY -> {
            // Cute cat ':3' mouth
            val w = radius * 0.09f
            drawArc(
                color = faceInk,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - w * 1.8f, mouthCenter.y - radius * 0.04f),
                size = Size(w * 1.8f, radius * 0.10f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawArc(
                color = faceInk,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x, mouthCenter.y - radius * 0.04f),
                size = Size(w * 1.8f, radius * 0.10f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        else -> {
            // Calm, polite, happy curve smile
            drawArc(
                color = faceInk,
                startAngle = 25f,
                sweepAngle = 130f,
                useCenter = false,
                topLeft = Offset(mouthCenter.x - radius * 0.13f, mouthCenter.y - radius * 0.08f),
                size = Size(radius * 0.26f, radius * 0.16f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }
    }

    // Danger Crying Tears (flowing animated sky-blue tear drops)
    if (danger.crying) {
        val tearTravel = (0.5f + sin(facePhase * 4.6f) * 0.5f) * radius * 0.24f
        val tearW = radius * 0.10f
        val tearH = radius * 0.22f
        drawOval(
            color = TearBlue.copy(alpha = 0.88f * alpha),
            topLeft = Offset(leftEyeCenter.x - tearW * 0.5f, eyeY + radius * 0.12f + tearTravel),
            size = Size(tearW, tearH),
        )
        drawOval(
            color = TearBlue.copy(alpha = 0.88f * alpha),
            topLeft = Offset(rightEyeCenter.x - tearW * 0.5f, eyeY + radius * 0.18f + radius * 0.24f - tearTravel),
            size = Size(tearW, tearH),
        )
    }
}

/**
 * Draws top calyx crowns, leaves, stems, and vine tendrils.
 */
private fun DrawScope.drawFruitCrownAndStem(
    level: FruitLevel,
    center: Offset,
    radius: Float,
    spec: FruitVisualSpec,
    alpha: Float,
) {
    when (level) {
            FruitLevel.BLUEBERRY -> {
                // 5-Point Star Calyx Crown
                val crownCenter = center + Offset(0f, -radius * 0.74f)
                repeat(5) { index ->
                    rotate(index * 72f, crownCenter) {
                        drawOval(
                            color = spec.leaf.copy(alpha = alpha),
                            topLeft = Offset(crownCenter.x - radius * 0.09f, crownCenter.y - radius * 0.26f),
                            size = Size(radius * 0.18f, radius * 0.30f),
                        )
                    }
                }
                drawCircle(color = spec.leafShadow.copy(alpha = alpha), radius = radius * 0.11f, center = crownCenter)
            }

            FruitLevel.RASPBERRY -> {
                // 3-Leaf Raspberry Calyx Hat
                val hatCenter = center + Offset(0f, -radius * 0.70f)
                repeat(3) { index ->
                    rotate((index - 1) * 36f, hatCenter) {
                        drawOval(
                            color = spec.leaf.copy(alpha = alpha),
                            topLeft = Offset(center.x - radius * 0.20f, center.y - radius * 0.98f),
                            size = Size(radius * 0.40f, radius * 0.32f),
                        )
                    }
                }
            }

            FruitLevel.STRAWBERRY -> {
                // 4-Leaf Strawberry Haircut Collar
                repeat(4) { index ->
                    val x = center.x + (index - 1.5f) * radius * 0.30f
                    drawOval(
                        color = spec.leaf.copy(alpha = alpha),
                        topLeft = Offset(x - radius * 0.22f, center.y - radius * 1.05f),
                        size = Size(radius * 0.44f, radius * 0.30f),
                    )
                }
            }

            FruitLevel.LIME -> {
                // Tiny twig stem + Leaf
                drawLine(
                    color = spec.stem.copy(alpha = alpha),
                    start = center + Offset(0f, -radius * 0.85f),
                    end = center + Offset(radius * 0.06f, -radius * 1.04f),
                    strokeWidth = (radius * 0.085f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.04f, center.y - radius * 1.10f),
                    size = Size(radius * 0.52f, radius * 0.26f),
                )
            }

            FruitLevel.MANDARIN -> {
                // Brown stem button + Deep green leaf
                drawCircle(color = spec.stem.copy(alpha = alpha), radius = radius * 0.09f, center = center + Offset(0f, -radius * 0.82f))
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.04f, center.y - radius * 1.04f),
                    size = Size(radius * 0.48f, radius * 0.24f),
                )
            }

            FruitLevel.APPLE -> {
                // Sturdy curved brown stem + pointed leaf
                drawLine(
                    color = spec.stem.copy(alpha = alpha),
                    start = center + Offset(0f, -radius * 0.72f),
                    end = center + Offset(radius * 0.08f, -radius * 1.08f),
                    strokeWidth = (radius * 0.095f).coerceAtLeast(1.2f),
                    cap = StrokeCap.Round,
                )
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.06f, center.y - radius * 1.12f),
                    size = Size(radius * 0.56f, radius * 0.28f),
                )
            }

            FruitLevel.PEAR -> {
                // Graceful curved stem + tilted leaf
                drawLine(
                    color = spec.stem.copy(alpha = alpha),
                    start = center + Offset(0f, -radius * 0.88f),
                    end = center + Offset(radius * 0.12f, -radius * 1.18f),
                    strokeWidth = (radius * 0.085f).coerceAtLeast(1f),
                    cap = StrokeCap.Round,
                )
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.10f, center.y - radius * 1.24f),
                    size = Size(radius * 0.58f, radius * 0.26f),
                )
            }

            FruitLevel.PEACH -> {
                // Twin leaves framing the top notch
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x - radius * 0.42f, center.y - radius * 1.05f),
                    size = Size(radius * 0.42f, radius * 0.22f),
                )
                drawOval(
                    color = spec.leaf.copy(alpha = alpha),
                    topLeft = Offset(center.x + radius * 0.02f, center.y - radius * 1.05f),
                    size = Size(radius * 0.42f, radius * 0.22f),
                )
            }

            FruitLevel.PINEAPPLE -> {
                // Spiky 5-frond green royal crown
                for (index in -2..2) {
                    val angle = index * 18f
                    val spireH = radius * (0.68f - abs(index) * 0.10f)
                    rotate(degrees = angle, pivot = center + Offset(0f, -radius * 0.70f)) {
                        drawOval(
                            color = (if (abs(index) == 2) spec.leafShadow else spec.leaf).copy(alpha = alpha),
                            topLeft = Offset(center.x - radius * 0.16f, center.y - radius * 0.70f - spireH),
                            size = Size(radius * 0.32f, spireH * 1.15f),
                        )
                    }
                }
            }

            FruitLevel.WATERMELON -> {
                // Cute curly pig-tail vine tendril
                drawArc(
                    color = spec.leaf.copy(alpha = alpha),
                    startAngle = 180f,
                    sweepAngle = 240f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.08f, center.y - radius * 1.16f),
                    size = Size(radius * 0.28f, radius * 0.24f),
                    style = Stroke(width = (radius * 0.065f).coerceAtLeast(1f), cap = StrokeCap.Round),
                )
            }
    }
}

// -------------------------------------------------------------------------------------------------
// Pre-allocated static geometry paths (normalized coordinates in -1..1 or 0..1 space)
// -------------------------------------------------------------------------------------------------

internal val StrawberryShape = Path().apply {
    moveTo(0f, -0.62f)
    cubicTo(-0.42f, -0.85f, -0.92f, -0.58f, -0.90f, -0.05f)
    cubicTo(-0.88f, 0.40f, -0.42f, 0.76f, 0f, 0.98f)
    cubicTo(0.42f, 0.76f, 0.88f, 0.40f, 0.90f, -0.05f)
    cubicTo(0.92f, -0.58f, 0.42f, -0.85f, 0f, -0.62f)
    close()
}

internal val AppleShape = Path().apply {
    moveTo(0f, -0.68f)
    cubicTo(-0.30f, -0.90f, -0.92f, -0.72f, -0.95f, -0.04f)
    cubicTo(-0.98f, 0.60f, -0.46f, 0.92f, 0f, 0.86f)
    cubicTo(0.46f, 0.92f, 0.98f, 0.60f, 0.95f, -0.04f)
    cubicTo(0.92f, -0.72f, 0.30f, -0.90f, 0f, -0.68f)
    close()
}

internal val PearShape = Path().apply {
    moveTo(0f, -0.98f)
    cubicTo(-0.44f, -0.86f, -0.46f, -0.46f, -0.68f, -0.18f)
    cubicTo(-0.98f, 0.16f, -0.90f, 0.88f, 0f, 0.94f)
    cubicTo(0.90f, 0.88f, 0.98f, 0.16f, 0.68f, -0.18f)
    cubicTo(0.46f, -0.46f, 0.44f, -0.86f, 0f, -0.98f)
    close()
}

// Pre-computed static offset collections
private val RaspberryDrupeletOffsets = listOf(
    Offset(0f, -0.52f),
    Offset(-0.32f, -0.30f),
    Offset(0.32f, -0.30f),
    Offset(-0.48f, 0.04f),
    Offset(0f, 0.02f),
    Offset(0.48f, 0.04f),
    Offset(-0.30f, 0.38f),
    Offset(0.30f, 0.38f),
    Offset(0f, 0.62f),
)

private val StrawberrySeedOffsets = listOf(
    Offset(-0.36f, -0.28f),
    Offset(0.28f, -0.34f),
    Offset(-0.48f, 0.08f),
    Offset(0.42f, 0.06f),
    Offset(-0.18f, 0.32f),
    Offset(0.24f, 0.42f),
    Offset(0.02f, 0.68f),
)

private val LimeWedgeAngles = listOf(18f, 138f, 258f)

private val PineappleScaleNodes = listOf(
    Offset(0f, -0.36f),
    Offset(-0.32f, -0.12f),
    Offset(0.32f, -0.12f),
    Offset(0f, 0.14f),
    Offset(-0.32f, 0.38f),
    Offset(0.32f, 0.38f),
)

private val DangerGlow = Color(0xFFFF8A65)
private val DangerActive = Color(0xFFD84F4A)
private val TearBlue = Color(0xFF70D6FF)
