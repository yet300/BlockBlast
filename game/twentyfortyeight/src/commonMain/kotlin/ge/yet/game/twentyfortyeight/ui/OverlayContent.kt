package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.common.utils.formatScore
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.twentyfortyeight.component.OverlayComponent
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.best
import ge.yet.game.twentyfortyeight.generated.resources.cancel
import ge.yet.game.twentyfortyeight.generated.resources.continue_game
import ge.yet.game.twentyfortyeight.generated.resources.games_ended_by_game_over
import ge.yet.game.twentyfortyeight.generated.resources.games_started
import ge.yet.game.twentyfortyeight.generated.resources.games_won
import ge.yet.game.twentyfortyeight.generated.resources.highest_tile_ever
import ge.yet.game.twentyfortyeight.generated.resources.restart
import ge.yet.game.twentyfortyeight.generated.resources.restart_confirmation_body
import ge.yet.game.twentyfortyeight.generated.resources.restart_confirmation_title
import ge.yet.game.twentyfortyeight.generated.resources.score
import ge.yet.game.twentyfortyeight.generated.resources.statistics
import ge.yet.game.twentyfortyeight.generated.resources.successful_moves
import ge.yet.game.twentyfortyeight.generated.resources.total_merges
import ge.yet.game.twentyfortyeight.generated.resources.total_score_earned
import ge.yet.game.twentyfortyeight.generated.resources.undo_uses
import ge.yet.game.twentyfortyeight.generated.resources.victory
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import ge.yet.game.uikit.components.sheet.ClaudeBottomSheet
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun OverlayContent(
    component: OverlayComponent,
    modifier: Modifier = Modifier,
    victoryFocusRequester: FocusRequester? = null,
) {
    when (component) {
        is OverlayComponent.Victory -> {
            val model by component.model.subscribeAsState()
            VictoryOverlay(
                model = model,
                onContinue = component::onContinueRequested,
                onRestart = component::onRestartRequested,
                onDismiss = component::onDismissRequested,
                modifier = modifier,
                focusRequester = victoryFocusRequester,
            )
        }
        is OverlayComponent.Statistics -> {
            val model by component.model.subscribeAsState()
            StatisticsOverlay(
                model = model,
                onDismiss = component::onDismissRequested,
                modifier = modifier,
            )
        }
        is OverlayComponent.RestartConfirmation -> {
            val model by component.model.subscribeAsState()
            RestartConfirmationOverlay(
                model = model,
                onConfirm = component::onConfirmRequested,
                onDismiss = component::onDismissRequested,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun VictoryOverlay(
    model: OverlayComponent.Model.Victory,
    onContinue: () -> Unit,
    onRestart: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    ClaudeBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        OverlayColumn {
            Text(
                text = stringResource(Res.string.victory),
                modifier = Modifier
                    .then(
                        focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
                    )
                    .focusable()
                    .semantics { traversalIndex = 5f }
                    .finiteEntryReveal(
                        normalDurationMs = MotionPolicy.Normal.crownMs,
                        delayMs = 0,
                    ),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OverlayStatisticRow(
                stringResource(Res.string.score),
                model.score.formatScore(),
                Modifier.finiteEntryReveal(
                    normalDurationMs = MotionPolicy.Normal.crownMs,
                    delayMs = 40,
                ),
            )
            OverlayStatisticRow(
                stringResource(Res.string.best),
                model.bestScore.formatScore(),
                Modifier.finiteEntryReveal(
                    normalDurationMs = MotionPolicy.Normal.crownMs,
                    delayMs = 60,
                ),
            )
            PrimaryTerracottaButton(
                text = stringResource(Res.string.continue_game),
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .finiteEntryReveal(
                        normalDurationMs = MotionPolicy.Normal.crownMs,
                        delayMs = MotionPolicy.Normal.victoryMaxStaggerMs,
                    ),
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.restart),
                onClick = onRestart,
                modifier = Modifier
                    .fillMaxWidth()
                    .finiteEntryReveal(
                        normalDurationMs = MotionPolicy.Normal.crownMs,
                        delayMs = MotionPolicy.Normal.victoryMaxStaggerMs,
                    ),
            )
        }
    }
}

@Composable
private fun StatisticsOverlay(
    model: OverlayComponent.Model.Statistics,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaudeBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        OverlayColumn {
            Text(
                text = stringResource(Res.string.statistics),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            OverlayStatisticRow(
                stringResource(Res.string.games_started),
                gamesValue(model.gamesStarted),
            )
            OverlayStatisticRow(
                stringResource(Res.string.games_won),
                gamesValue(model.gamesWon),
            )
            OverlayStatisticRow(
                stringResource(Res.string.games_ended_by_game_over),
                gamesValue(model.gamesEndedByGameOver),
            )
            OverlayStatisticRow(
                stringResource(Res.string.successful_moves),
                movesValue(model.successfulMoves),
            )
            OverlayStatisticRow(
                stringResource(Res.string.total_merges),
                mergesValue(model.totalMerges),
            )
            OverlayStatisticRow(
                stringResource(Res.string.total_score_earned),
                model.totalScoreEarned.formatScore(),
            )
            OverlayStatisticRow(
                stringResource(Res.string.highest_tile_ever),
                model.highestTileEver.formatScore(),
            )
            OverlayStatisticRow(
                stringResource(Res.string.undo_uses),
                undoUsesValue(model.undoUses),
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun RestartConfirmationOverlay(
    model: OverlayComponent.Model.RestartConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ClaudeBottomSheet(onDismiss = onDismiss, modifier = modifier) {
        OverlayColumn {
            Text(
                text = stringResource(Res.string.restart_confirmation_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(
                    Res.string.restart_confirmation_body,
                    model.score.formatScore(),
                    movesValue(model.successfulMovesInRun),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            PrimaryTerracottaButton(
                text = stringResource(Res.string.restart),
                onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryWarmSandButton(
                text = stringResource(Res.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun OverlayColumn(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
    }
}

@Composable
private fun OverlayStatisticRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
