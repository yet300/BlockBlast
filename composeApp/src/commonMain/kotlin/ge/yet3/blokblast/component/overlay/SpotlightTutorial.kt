package ge.yet3.blokblast.component.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.tutorial_done
import blockblast.composeapp.generated.resources.tutorial_grid_body
import blockblast.composeapp.generated.resources.tutorial_grid_title
import blockblast.composeapp.generated.resources.tutorial_next
import blockblast.composeapp.generated.resources.tutorial_skip
import blockblast.composeapp.generated.resources.tutorial_tray_body
import blockblast.composeapp.generated.resources.tutorial_tray_title
import org.jetbrains.compose.resources.stringResource

/**
 * One step of the spotlight tutorial: a screen-space rectangle to highlight,
 * plus the callout text. [target] in root pixels; pass [Rect.Zero] to dim
 * everything (no cutout).
 */
data class SpotlightStep(
    val target: Rect,
    val title: String,
    val body: String,
)

/**
 * Full-screen scrim with a rounded cutout around the current step's target
 * and a callout card placed just below it. Walks through [steps] and calls
 * [onFinished] on the final tap or skip.
 *
 * Touches on the scrim are absorbed so the user cannot interact with the
 * underlying UI while the tutorial is up.
 */
@Composable
fun SpotlightTutorial(
    steps: List<SpotlightStep>,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (steps.isEmpty()) return
    var index by remember { mutableIntStateOf(0) }
    val safeIndex = index.coerceIn(0, steps.lastIndex)
    val step = steps[safeIndex]
    val isLast = safeIndex >= steps.lastIndex

    val density = LocalDensity.current
    val padPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }

    val left by animateFloatAsState(step.target.left, tween(280), label = "spotlight-l")
    val top by animateFloatAsState(step.target.top, tween(280), label = "spotlight-t")
    val right by animateFloatAsState(step.target.right, tween(280), label = "spotlight-r")
    val bottom by animateFloatAsState(step.target.bottom, tween(280), label = "spotlight-b")

    val scrimColor = Color.Black.copy(alpha = 0.72f)
    val ringColor = MaterialTheme.colorScheme.primary

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    }
                }
            }
            // BlendMode.Clear needs an offscreen layer to actually punch a hole.
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
            .drawWithCache {
                // Pass the four floats directly — animateFloatAsState is per-frame
                // and constructing a Rect/Offset/Size each time would allocate on
                // every animation tick. drawWithCache only re-runs when the keys
                // referenced inside change, so we still get caching of the lambda.
                onDrawWithContent {
                    drawRect(scrimColor)
                    val hasTarget = right > left && bottom > top
                    if (hasTarget) {
                        val holeLeft = left - padPx
                        val holeTop = top - padPx
                        val holeWidth = (right - left) + 2f * padPx
                        val holeHeight = (bottom - top) + 2f * padPx
                        val topLeft = Offset(holeLeft, holeTop)
                        val size = Size(holeWidth, holeHeight)
                        val corner = CornerRadius(cornerPx, cornerPx)
                        drawRoundRect(
                            color = Color.Transparent,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = corner,
                            blendMode = BlendMode.Clear,
                        )
                        drawRoundRect(
                            color = ringColor,
                            topLeft = topLeft,
                            size = size,
                            cornerRadius = corner,
                            style = Stroke(width = 4f),
                        )
                    }
                    drawContent()
                }
            },
    ) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        Callout(
            targetTop = top,
            targetBottom = bottom,
            screenHeightPx = screenHeightPx,
            targetVisible = right > left && bottom > top,
            title = step.title,
            body = step.body,
            isLast = isLast,
            onNext = { if (isLast) onFinished() else index = safeIndex + 1 },
            onSkip = onFinished,
        )
    }
}

@Composable
private fun BoxScope.Callout(
    targetTop: Float,
    targetBottom: Float,
    screenHeightPx: Float,
    targetVisible: Boolean,
    title: String,
    body: String,
    isLast: Boolean,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { 16.dp.toPx() }
    val hasTarget = targetVisible

    val cardModifier = Modifier
        .padding(horizontal = 24.dp)
        .widthIn(max = 360.dp)
        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
        .padding(20.dp)

    // Heuristic: place the callout where there is more vertical space.
    // This prevents the card from being pushed off-screen or behind system bars.
    val spaceAbove = targetTop
    val spaceBelow = screenHeightPx - targetBottom
    val preferAbove = hasTarget && spaceBelow < spaceAbove

    Column(
        modifier = if (!hasTarget) {
            Modifier.align(Alignment.Center).then(cardModifier)
        } else if (preferAbove) {
            Modifier
                .align(Alignment.BottomCenter)
                // Alignment.BottomCenter puts the bottom of the card at screenHeightPx.
                // We offset it up (negative y) so its bottom is at targetTop - gapPx.
                .offset { IntOffset(0, (-(screenHeightPx - targetTop + gapPx)).toInt()) }
                .then(cardModifier)
        } else {
            Modifier
                .align(Alignment.TopCenter)
                // Alignment.TopCenter puts the top of the card at y=0.
                // We offset it down so its top is at targetBottom + gapPx.
                .offset { IntOffset(0, (targetBottom + gapPx).toInt()) }
                .then(cardModifier)
        },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!isLast) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(Res.string.tutorial_skip))
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onNext) {
                Text(
                    stringResource(
                        if (isLast) Res.string.tutorial_done else Res.string.tutorial_next
                    ),
                )
            }
        }
    }
}

/** Standard 2-step tutorial: tray then board. */
@Composable
fun rememberGameTutorialSteps(
    trayBounds: Rect,
    gridBounds: Rect,
): List<SpotlightStep> {
    val trayTitle = stringResource(Res.string.tutorial_tray_title)
    val trayBody = stringResource(Res.string.tutorial_tray_body)
    val gridTitle = stringResource(Res.string.tutorial_grid_title)
    val gridBody = stringResource(Res.string.tutorial_grid_body)
    return remember(trayBounds, gridBounds, trayTitle, trayBody, gridTitle, gridBody) {
        listOf(
            SpotlightStep(target = trayBounds, title = trayTitle, body = trayBody),
            SpotlightStep(target = gridBounds, title = gridTitle, body = gridBody),
        )
    }
}
