package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.loading_game
import ge.yet.game.twentyfortyeight.generated.resources.new_game_not_saved
import ge.yet.game.twentyfortyeight.generated.resources.progress_not_saved
import ge.yet.game.twentyfortyeight.generated.resources.skip
import ge.yet.game.twentyfortyeight.generated.resources.supporting_hint
import ge.yet.game.twentyfortyeight.generated.resources.tutorial_instruction
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.uikit.adaptive.AdaptiveGameScaffold
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayingContent(
    model: PlayingComponent.Model,
    onDirection: (Direction) -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onStatistics: () -> Unit,
    onSkipTutorial: () -> Unit,
    modifier: Modifier = Modifier,
    error: UiErrorCode? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("gameplay_viewport"),
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveGameScaffold(
            modifier = Modifier.fillMaxSize(),
            supportingPaneModifier = Modifier.testTag("supporting_column"),
            primary = {
                BoardOrLoading(
                    model = model,
                    onDirection = onDirection,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                )
            },
            supporting = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ScoreBestRow(
                        score = model.score,
                        bestScore = model.bestScore,
                        onStatistics = onStatistics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SupportingContent(
                        model = model,
                        onUndo = onUndo,
                        onRestart = onRestart,
                        onSkipTutorial = onSkipTutorial,
                        error = error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }
}

@Composable
private fun BoardOrLoading(
    model: PlayingComponent.Model,
    onDirection: (Direction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val board = model.board
    if (board == null) {
        Box(
            modifier = modifier.testTag("game_board"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.loading_game),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    } else {
        TwentyFortyEightBoard(
            model = BoardModel(board, model.transition),
            onDirection = onDirection,
            modifier = modifier.testTag("game_board"),
        )
    }
}

@Composable
private fun SupportingContent(
    model: PlayingComponent.Model,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onSkipTutorial: () -> Unit,
    error: UiErrorCode?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        GameActions(
            undoEnabled = model.undoEnabled,
            onUndo = onUndo,
            onRestart = onRestart,
        )
        Text(
            text = stringResource(Res.string.supporting_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (model.tutorialVisible) {
            Text(
                text = stringResource(Res.string.tutorial_instruction),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.skip),
                onClick = onSkipTutorial,
            )
        }
        val effectiveError = error ?: if (
            model.persistenceStatus == PlayingComponent.PersistenceStatus.Dirty
        ) {
            UiErrorCode.ProgressNotSaved
        } else {
            null
        }
        effectiveError?.let { code ->
            Text(
                text = errorText(code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
internal fun errorText(code: UiErrorCode): String = when (code) {
    UiErrorCode.ProgressNotSaved -> stringResource(Res.string.progress_not_saved)
    UiErrorCode.NewGameNotSaved -> stringResource(Res.string.new_game_not_saved)
}
