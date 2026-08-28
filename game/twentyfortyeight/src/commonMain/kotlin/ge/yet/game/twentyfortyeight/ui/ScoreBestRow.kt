package ge.yet.game.twentyfortyeight.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.common.utils.formatScore
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.best
import ge.yet.game.twentyfortyeight.generated.resources.best_description
import ge.yet.game.twentyfortyeight.generated.resources.best_new_description
import ge.yet.game.twentyfortyeight.generated.resources.crown_description
import ge.yet.game.twentyfortyeight.generated.resources.score
import ge.yet.game.twentyfortyeight.generated.resources.score_description
import ge.yet.game.twentyfortyeight.generated.resources.statistics_description
import ge.yet.game.twentyfortyeight.store.VisualTransition
import ge.yet.game.uikit.components.icon.Crown
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ScoreBestRow(
    score: Long,
    bestScore: Long,
    onStatistics: () -> Unit,
    transition: VisualTransition? = null,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) {
            ScoreCard(
                label = stringResource(Res.string.score),
                value = score.formatScore(),
                description = stringResource(Res.string.score_description, score.formatScore()),
                modifier = Modifier
                    .fillMaxWidth()
                    .scorePulse(score, MotionPolicy.Normal.scoreMs),
            )
            val move = transition as? VisualTransition.Move
            if (move != null && move.result.scoreDelta > 0L) {
                ScoreDeltaChip(
                    transitionId = move.transitionId,
                    scoreDelta = move.result.scoreDelta,
                    modifier = Modifier.align(Alignment.TopEnd),
                )
            }
        }
        BestCard(
            bestScore = bestScore,
            isNewBest = score > 0L && score >= bestScore,
            onStatistics = onStatistics,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ScoreDeltaChip(
    transitionId: Long,
    scoreDelta: Long,
    modifier: Modifier = Modifier,
) {
    val policy = rememberMotionPolicy()
    val progress = remember(transitionId) { Animatable(0f) }
    LaunchedEffect(transitionId, policy) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (policy.usesSpatialMotion) {
                    MotionPolicy.Normal.scoreMs
                } else {
                    MotionPolicy.Reduced.alphaMs
                },
                easing = LinearEasing,
            ),
        )
    }
    Text(
        text = "+${scoreDelta.formatScore()}",
        modifier = modifier.graphicsLayer {
            val fade = if (progress.value < 0.5f) {
                progress.value * 2f
            } else {
                (1f - progress.value) * 2f
            }
            alpha = fade.coerceIn(0f, 1f)
            if (policy.usesSpatialMotion) {
                translationY = -progress.value * 16.dp.toPx()
            }
        },
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ScoreCard(
    label: String,
    value: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics(mergeDescendants = true) {
                traversalIndex = 0f
                contentDescription = description
            }
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun BestCard(
    bestScore: Long,
    isNewBest: Boolean,
    onStatistics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatted = bestScore.formatScore()
    val bestDescription = if (isNewBest) {
        stringResource(Res.string.best_new_description, formatted)
    } else {
        stringResource(Res.string.best_description, formatted)
    }
    val statisticsDescription = stringResource(Res.string.statistics_description)
    val crownDescription = stringResource(Res.string.crown_description)

    Surface(
        onClick = onStatistics,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics {
                traversalIndex = 1f
                contentDescription = statisticsDescription
                stateDescription = bestDescription
            },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Crown,
                    contentDescription = crownDescription,
                    modifier = Modifier
                        .size(18.dp)
                        .scorePulse(bestScore, MotionPolicy.Normal.crownMs),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(Res.string.best),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatted,
                modifier = Modifier.semantics { contentDescription = bestDescription },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Modifier.scorePulse(
    eventKey: Long,
    normalDurationMs: Int,
): Modifier {
    val policy = rememberMotionPolicy()
    val progress = remember { Animatable(1f) }
    var previousKey by remember { mutableStateOf(eventKey) }
    LaunchedEffect(eventKey, policy) {
        if (previousKey == eventKey) return@LaunchedEffect
        previousKey = eventKey
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = if (policy.usesSpatialMotion) {
                    normalDurationMs
                } else {
                    MotionPolicy.Reduced.alphaMs
                },
                easing = LinearEasing,
            ),
        )
    }
    return graphicsLayer {
        if (policy.usesSpatialMotion) {
            val pulse = 1f - progress.value
            scaleX = 1f + pulse * 0.08f
            scaleY = 1f + pulse * 0.08f
        } else {
            alpha = 0.7f + progress.value * 0.3f
        }
    }
}
