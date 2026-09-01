package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import kotlin.math.min
import kotlin.math.sin

internal data class ActiveFruitPresentation(
    val id: Long,
    val event: FruitMergeComponent.PresentationEvent,
    val startedAtSeconds: Float,
)

internal fun ActiveFruitPresentation.isExpired(nowSeconds: Float): Boolean =
    nowSeconds - startedAtSeconds >= event.presentationDurationSeconds()

private fun FruitMergeComponent.PresentationEvent.presentationDurationSeconds(): Float = when (this) {
    is FruitMergeComponent.PresentationEvent.Landing -> 0.28f
    is FruitMergeComponent.PresentationEvent.Merge -> MERGE_PRESENTATION_SECONDS
    is FruitMergeComponent.PresentationEvent.Clear -> 0.30f
    is FruitMergeComponent.PresentationEvent.ShakePulse -> 0.18f
}

@Composable
internal fun FruitMergePresentation(
    events: List<ActiveFruitPresentation>,
    nowSeconds: Float,
    boardBoundsInViewport: Rect,
    modifier: Modifier = Modifier,
) {
    if (events.isEmpty() || boardBoundsInViewport == Rect.Zero) return
    val palette = rememberFruitMergePalette()
    Canvas(modifier) {
        val side = min(boardBoundsInViewport.width, boardBoundsInViewport.height)
        val origin = Offset(
            boardBoundsInViewport.left + (boardBoundsInViewport.width - side) * 0.5f,
            boardBoundsInViewport.top + (boardBoundsInViewport.height - side) * 0.5f,
        )
        fun world(position: ge.yet.game.fruitmerge.engine.Vec2): Offset =
            origin + Offset(position.x * side, position.y * side)

        events.forEach { active ->
            val elapsed = (nowSeconds - active.startedAtSeconds).coerceAtLeast(0f)
            val duration = active.event.presentationDurationSeconds()
            val progress = (elapsed / duration).coerceIn(0f, 1f)
            when (val event = active.event) {
                is FruitMergeComponent.PresentationEvent.Landing -> {
                    val center = world(event.position)
                    val radius = event.level.radius * side
                    val alpha = 1f - progress
                    drawArc(
                        color = palette.woodDark.copy(alpha = 0.55f * alpha),
                        startAngle = 205f,
                        sweepAngle = 130f,
                        useCenter = false,
                        topLeft = center - Offset(radius * (1.2f + progress), radius * 0.45f),
                        size = Size(radius * (2.4f + progress * 2f), radius * 0.9f),
                        style = Stroke((side * 0.006f).coerceAtLeast(1f), cap = StrokeCap.Round),
                    )
                }
                is FruitMergeComponent.PresentationEvent.Merge -> {
                    val center = world(event.position)
                    val squeeze = mergeSqueeze(mergePresentationProgress(elapsed))
                    val radius = event.level.radius * side
                    drawOval(
                        color = palette.coral.copy(alpha = (1f - progress) * 0.16f),
                        topLeft = center - Offset(radius * squeeze.scaleX, radius * squeeze.scaleY),
                        size = Size(radius * 2f * squeeze.scaleX, radius * 2f * squeeze.scaleY),
                    )
                    drawFruit(
                        level = event.level,
                        center = center,
                        radius = radius * (0.88f + progress * 0.12f),
                        angleRadians = 0f,
                        verticalVelocity = 0f,
                        impact = 0f,
                        facePhase = progress,
                        danger = DangerVisual(0f, false),
                        alpha = (1f - progress * 0.35f).coerceIn(0f, 1f),
                        merging = true,
                    )
                }
                is FruitMergeComponent.PresentationEvent.Clear -> {
                    val center = world(event.position)
                    val radius = event.level.radius * side
                    val travel = radius * progress * 0.85f
                    val alpha = 1f - progress
                    drawLine(
                        color = palette.paper.copy(alpha = alpha),
                        start = center + Offset(-radius + travel, radius - travel),
                        end = center + Offset(radius + travel, -radius - travel),
                        strokeWidth = (side * 0.014f).coerceAtLeast(2f),
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = palette.coral.copy(alpha = alpha),
                        start = center + Offset(-radius * 0.88f + travel, radius * 0.88f - travel),
                        end = center + Offset(radius * 0.88f + travel, -radius * 0.88f - travel),
                        strokeWidth = (side * 0.005f).coerceAtLeast(1f),
                        cap = StrokeCap.Round,
                    )
                }
                is FruitMergeComponent.PresentationEvent.ShakePulse -> {
                    val wave = sin(progress * kotlin.math.PI.toFloat())
                    val inset = side * (0.018f + wave * 0.012f)
                    drawRoundRect(
                        color = palette.woodLight.copy(alpha = (1f - progress) * 0.58f),
                        topLeft = origin - Offset(inset, inset),
                        size = Size(side + inset * 2f, side + inset * 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(side * 0.055f),
                        style = Stroke((side * 0.008f).coerceAtLeast(1f)),
                    )
                }
            }
        }
    }
}
