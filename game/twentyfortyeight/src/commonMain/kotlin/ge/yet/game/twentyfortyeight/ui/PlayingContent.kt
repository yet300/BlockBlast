package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ge.yet.game.twentyfortyeight.component.PlayingComponent
import ge.yet.game.twentyfortyeight.engine.Direction
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.loading_game
import ge.yet.game.twentyfortyeight.generated.resources.new_game_not_saved
import ge.yet.game.twentyfortyeight.generated.resources.progress_not_saved
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.uikit.adaptive.AdaptiveGameScaffold
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PlayingContent(
    model: PlayingComponent.Model,
    onDirection: (Direction) -> Unit,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
    onSkipTutorial: () -> Unit,
    onTransitionCompleted: (Long) -> Unit = {},
    boardFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
    error: UiErrorCode? = null,
) {
    var viewportOriginInRoot by remember { mutableStateOf(Offset.Zero) }
    var supportBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
    val supportBoundsInViewport = supportBoundsInRoot?.translate(-viewportOriginInRoot)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { viewportOriginInRoot = it.positionInRoot() }
            .detectTwentyFortyEightSwipes(
                enabled = model.gesturesEnabled,
                supportBoundsInViewport = supportBoundsInViewport,
                onDirection = onDirection,
            )
            .testTag("gameplay_viewport"),
        contentAlignment = Alignment.Center,
    ) {
        AdaptiveGameScaffold(
            modifier = Modifier.fillMaxSize(),
            supportingPaneModifier = Modifier
                .onGloballyPositioned { supportBoundsInRoot = it.boundsInRoot() }
                .testTag("supporting_column"),
            primary = {
                BoardOrLoading(
                    model = model,
                    onDirection = onDirection,
                    onTransitionCompleted = onTransitionCompleted,
                    boardFocusRequester = boardFocusRequester,
                    onSkipTutorial = onSkipTutorial,
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
                        bestImprovedInRun = model.bestImprovedInRun,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SupportingContent(
                        model = model,
                        onUndo = onUndo,
                        onRestart = onRestart,
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
    onTransitionCompleted: (Long) -> Unit,
    boardFocusRequester: FocusRequester?,
    onSkipTutorial: () -> Unit,
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
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxSize(),
                contentAlignment = Alignment.BottomCenter,
            ) {
                TwentyFortyEightBoard(
                    model = BoardModel(board, model.transition),
                    onDirection = onDirection,
                    onTransitionCompleted = onTransitionCompleted,
                    focusRequester = boardFocusRequester,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("game_board"),
                )
                TutorialOverlay(
                    visible = model.tutorialVisible,
                    active = model.gesturesEnabled,
                    policy = rememberMotionPolicy(),
                    onSkip = onSkipTutorial,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SupportingContent(
    model: PlayingComponent.Model,
    onUndo: () -> Unit,
    onRestart: () -> Unit,
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
    }
}

@Composable
internal fun errorText(code: UiErrorCode): String = when (code) {
    UiErrorCode.ProgressNotSaved -> stringResource(Res.string.progress_not_saved)
    UiErrorCode.NewGameNotSaved -> stringResource(Res.string.new_game_not_saved)
}
