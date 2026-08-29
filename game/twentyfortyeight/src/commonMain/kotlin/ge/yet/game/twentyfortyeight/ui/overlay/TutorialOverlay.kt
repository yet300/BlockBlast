package ge.yet.game.twentyfortyeight.ui.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.skip
import ge.yet.game.twentyfortyeight.generated.resources.tutorial_instruction
import ge.yet.game.twentyfortyeight.ui.motion.MotionPolicy
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import org.jetbrains.compose.resources.stringResource

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
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = tutorialMotionMode(visible, active, policy)
    val progress = remember { Animatable(1f) }

    LaunchedEffect(mode) {
        progress.snapTo(if (mode == TutorialMotionMode.Animated) 0f else 1f)
        if (mode != TutorialMotionMode.Animated) return@LaunchedEffect
        repeat(TutorialRepeatCount) {
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(TutorialSwipeMs, easing = LinearEasing),
            )
            progress.snapTo(0f)
        }
        progress.snapTo(1f)
    }

    if (mode == TutorialMotionMode.Hidden) return

    val instruction = stringResource(Res.string.tutorial_instruction)
    Surface(
        modifier = modifier
            .testTag("tutorial")
            .semantics(mergeDescendants = true) {
                traversalIndex = 5f
                contentDescription = instruction
            },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TutorialSwipeIllustration(
                progress = { progress.value },
                modifier = Modifier.size(width = 72.dp, height = 28.dp),
            )
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.skip),
                onClick = onSkip,
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun TutorialSwipeIllustration(
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = modifier
            .graphicsLayer {
                translationX = (progress().coerceIn(0f, 1f) - 0.5f) * size.width * 0.45f
            }
            .testTag("tutorial_illustration"),
    ) {
        val centerY = size.height / 2f
        val startX = size.width * 0.2f
        val endX = size.width * 0.8f
        drawLine(
            color = color,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(endX, centerY),
            end = Offset(endX - 8.dp.toPx(), centerY - 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(endX, centerY),
            end = Offset(endX - 8.dp.toPx(), centerY + 7.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private const val TutorialRepeatCount = 3
private const val TutorialSwipeMs = 640
