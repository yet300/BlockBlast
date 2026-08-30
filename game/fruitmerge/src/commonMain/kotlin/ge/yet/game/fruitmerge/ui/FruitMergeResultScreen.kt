package ge.yet.game.fruitmerge.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.best_score
import ge.yet.game.fruitmerge.generated.resources.game_over
import ge.yet.game.fruitmerge.generated.resources.game_over_supporting
import ge.yet.game.fruitmerge.generated.resources.largest_fruit
import ge.yet.game.fruitmerge.generated.resources.new_game
import ge.yet.game.fruitmerge.generated.resources.score
import ge.yet.game.fruitmerge.session.FruitMergeResultComponent
import ge.yet.game.uikit.components.background.AmbientMeshBackground
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FruitMergeResultScreen(
    component: FruitMergeResultComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    val reducedMotion = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val transition = rememberInfiniteTransition(label = "result-fruit-face")
    val faceTime by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion) 0f else 8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "result-fruit-face-time",
    )
    val scoreLabel = stringResource(Res.string.score)
    val bestLabel = stringResource(Res.string.best_score)
    val largestLabel = stringResource(Res.string.largest_fruit)
    val fruitName = stringResource(fruitNameResource(model.largestFruit))

    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { testTag = FruitMergeTestTags.Result },
        contentAlignment = Alignment.Center,
    ) {
        AmbientMeshBackground(
            baseColor = MaterialTheme.colorScheme.primaryContainer,
            animated = !reducedMotion,
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(Res.string.game_over),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(Res.string.game_over_supporting),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).widthIn(max = 440.dp),
            )
            FruitPreview(
                level = model.largestFruit,
                faceTimeSeconds = faceTime,
                reducedMotion = reducedMotion,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .size(176.dp)
                    .semantics {
                        testTag = FruitMergeTestTags.ResultLargestFruit
                        contentDescription = "$largestLabel: $fruitName"
                    },
            )
            Text(
                text = fruitName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(
                modifier = Modifier.padding(top = 18.dp).widthIn(max = 440.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ResultStat(
                    label = scoreLabel,
                    value = model.score,
                    testTag = FruitMergeTestTags.ResultScore,
                    modifier = Modifier.weight(1f),
                )
                ResultStat(
                    label = bestLabel,
                    value = model.bestScore,
                    testTag = FruitMergeTestTags.ResultBest,
                    modifier = Modifier.weight(1f),
                )
            }
            PrimaryTerracottaButton(
                text = stringResource(Res.string.new_game),
                onClick = component::newGame,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .widthIn(max = 440.dp)
                    .fillMaxWidth()
                    .semantics { testTag = FruitMergeTestTags.NewGame },
            )
        }
    }
}

@Composable
private fun ResultStat(
    label: String,
    value: Long,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics {
            this.testTag = testTag
            contentDescription = "$label: $value"
        },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
