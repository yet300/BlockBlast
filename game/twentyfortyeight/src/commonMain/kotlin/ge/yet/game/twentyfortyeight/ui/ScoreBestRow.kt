package ge.yet.game.twentyfortyeight.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.common.utils.formatScore
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.best_description
import ge.yet.game.twentyfortyeight.generated.resources.score_description
import ge.yet.game.uikit.components.icon.Crown
import org.jetbrains.compose.resources.stringResource

internal enum class ScoreCardState {
    ScoreOnly,
    ScoreAndBest,
    BestOnly,
}

internal fun scoreCardState(
    bestScore: Long,
    bestImprovedInRun: Boolean,
): ScoreCardState = when {
    bestImprovedInRun -> ScoreCardState.BestOnly
    bestScore > 0L -> ScoreCardState.ScoreAndBest
    else -> ScoreCardState.ScoreOnly
}

@Composable
internal fun ScoreBestRow(
    score: Long,
    bestScore: Long,
    bestImprovedInRun: Boolean,
    modifier: Modifier = Modifier,
) {
    val state = scoreCardState(bestScore, bestImprovedInRun)
    val scoreText = score.formatScore()
    val bestText = bestScore.formatScore()
    val scoreDescription = stringResource(Res.string.score_description, scoreText)
    val description = if (state == ScoreCardState.ScoreOnly) {
        scoreDescription
    } else {
        "$scoreDescription. ${stringResource(Res.string.best_description, bestText)}"
    }
    val policy = rememberMotionPolicy()
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .testTag("score_card")
            .semantics(mergeDescendants = true) {
                traversalIndex = 0f
                contentDescription = description
            }
            .focusable(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        AnimatedContent(
            targetState = state,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
            transitionSpec = {
                val duration = if (policy.usesSpatialMotion) {
                    MotionPolicy.Normal.scoreMs
                } else {
                    MotionPolicy.Reduced.alphaMs
                }
                if (policy.usesSpatialMotion) {
                    (fadeIn(tween(duration)) + scaleIn(tween(duration), initialScale = 0.92f))
                        .togetherWith(
                            fadeOut(tween(duration)) +
                                scaleOut(tween(duration), targetScale = 0.92f),
                        )
                } else {
                    fadeIn(tween(duration)).togetherWith(fadeOut(tween(duration)))
                }
            },
            label = "score-record-state",
        ) {
            ScoreCardContent(
                state = it,
                score = scoreText,
                bestScore = bestText,
                scoreKey = score,
                bestScoreKey = bestScore,
            )
        }
    }
}

@Composable
private fun ScoreCardContent(
    state: ScoreCardState,
    score: String,
    bestScore: String,
    scoreKey: Long,
    bestScoreKey: Long,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state != ScoreCardState.BestOnly) {
            Text(
                text = score,
                modifier = Modifier
                    .testTag("score_value")
                    .scorePulse(scoreKey, MotionPolicy.Normal.scoreMs),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (state == ScoreCardState.ScoreAndBest) Spacer(Modifier.width(20.dp))
        if (state != ScoreCardState.ScoreOnly) {
            Icon(
                imageVector = Crown,
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .testTag("best_crown")
                    .scorePulse(bestScoreKey, MotionPolicy.Normal.crownMs),
                tint = MaterialTheme.colorScheme.tertiary,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = bestScore,
                modifier = Modifier.testTag("best_value"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
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
