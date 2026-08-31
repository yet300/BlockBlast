package ge.yet.game.fruitmerge.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ge.yet.game.fruitmerge.generated.resources.Res
import ge.yet.game.fruitmerge.generated.resources.best_score
import ge.yet.game.fruitmerge.generated.resources.game_over
import ge.yet.game.fruitmerge.generated.resources.largest_fruit
import ge.yet.game.fruitmerge.generated.resources.new_game
import ge.yet.game.fruitmerge.generated.resources.score
import ge.yet.game.fruitmerge.session.FruitMergeComponent
import ge.yet.game.uikit.components.score.compactScore
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun FruitMergeGameOverOverlay(
    screen: FruitMergeComponent.ScreenState.GameOver,
    faceTimeSeconds: Float,
    reducedMotion: Boolean,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val game = screen.game
    val palette = rememberFruitMergePalette()
    val scoreState = scoreCardState(game.bestScore, game.bestImprovedInRun)
    val largestFruitLabel = stringResource(Res.string.largest_fruit)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.ink.copy(alpha = 0.34f))
            .semantics { testTag = FruitMergeTestTags.Result },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .padding(24.dp)
                .widthIn(max = 340.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = palette.paper,
            contentColor = palette.ink,
            border = BorderStroke(3.dp, palette.woodDark.copy(alpha = 0.72f)),
            shadowElevation = 10.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = stringResource(Res.string.game_over),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = palette.ink,
                )
                FruitPreview(
                    level = screen.largestFruit,
                    faceTimeSeconds = faceTimeSeconds,
                    reducedMotion = reducedMotion,
                    modifier = Modifier
                        .size(92.dp)
                        .semantics {
                            testTag = FruitMergeTestTags.ResultLargestFruit
                            contentDescription = largestFruitLabel
                        },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    if (scoreState != ScoreCardState.BEST_ONLY) {
                        ResultValue(
                            label = stringResource(Res.string.score),
                            value = game.score,
                            tag = FruitMergeTestTags.ResultScore,
                        )
                    }
                    if (scoreState != ScoreCardState.SCORE_ONLY) {
                        ResultValue(
                            label = if (scoreState == ScoreCardState.BEST_ONLY) {
                                "♛ " + stringResource(Res.string.best_score)
                            } else {
                                stringResource(Res.string.best_score)
                            },
                            value = game.bestScore,
                            tag = FruitMergeTestTags.ResultBest,
                        )
                    }
                }
                Button(
                    onClick = onNewGame,
                    modifier = Modifier.semantics { testTag = FruitMergeTestTags.NewGame },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.coral,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(stringResource(Res.string.new_game), fontWeight = FontWeight.Black)
                }
            }
        }
    }
}

@Composable
private fun ResultValue(label: String, value: Long, tag: String) {
    Column(
        modifier = Modifier.semantics {
            testTag = tag
            contentDescription = "$label $value"
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall)
        Text(compactScore(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
    }
}
