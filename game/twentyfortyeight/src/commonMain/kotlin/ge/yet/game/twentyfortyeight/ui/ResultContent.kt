package ge.yet.game.twentyfortyeight.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.app.common.utils.formatScore
import ge.yet.game.twentyfortyeight.component.ResultComponent
import ge.yet.game.twentyfortyeight.generated.resources.Res
import ge.yet.game.twentyfortyeight.generated.resources.best
import ge.yet.game.twentyfortyeight.generated.resources.game_over
import ge.yet.game.twentyfortyeight.generated.resources.games_count
import ge.yet.game.twentyfortyeight.generated.resources.games_won
import ge.yet.game.twentyfortyeight.generated.resources.highest_tile
import ge.yet.game.twentyfortyeight.generated.resources.merges_count
import ge.yet.game.twentyfortyeight.generated.resources.moves_count
import ge.yet.game.twentyfortyeight.generated.resources.new_game
import ge.yet.game.twentyfortyeight.generated.resources.score
import ge.yet.game.twentyfortyeight.generated.resources.successful_moves
import ge.yet.game.twentyfortyeight.generated.resources.total_merges
import ge.yet.game.twentyfortyeight.generated.resources.undo_uses_count
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.uikit.adaptive.AdaptiveGameScaffold
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ResultContent(
    model: ResultComponent.Model,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    error: UiErrorCode? = null,
    resultFocusRequester: FocusRequester? = null,
) {
    AdaptiveGameScaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("result_viewport"),
        supportingPaneModifier = Modifier.testTag("result_supporting_column"),
        primary = {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .finiteEntryReveal(MotionPolicy.Normal.gameOverMs),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        text = stringResource(Res.string.game_over),
                        modifier = Modifier
                            .then(
                                resultFocusRequester?.let { Modifier.focusRequester(it) }
                                    ?: Modifier,
                            )
                            .focusable(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ResultSummary(model = model, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        supporting = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ResultStatistics(
                        statistics = model.statistics,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    error?.let { code ->
                        Text(
                            text = errorText(code),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                    PrimaryTerracottaButton(
                        text = stringResource(Res.string.new_game),
                        onClick = onNewGame,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun ResultSummary(
    model: ResultComponent.Model,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultSummaryRow(stringResource(Res.string.score), model.score.formatScore())
        ResultSummaryRow(stringResource(Res.string.best), model.bestScore.formatScore())
        ResultSummaryRow(stringResource(Res.string.highest_tile), model.highestTile.formatScore())
    }
}

@Composable
private fun ResultSummaryRow(
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
            style = MaterialTheme.typography.bodyMedium,
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

@Composable
private fun ResultStatistics(
    statistics: ResultComponent.SelectedStatistics,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultSummaryRow(stringResource(Res.string.games_won), gamesValue(statistics.gamesWon))
        ResultSummaryRow(
            stringResource(Res.string.successful_moves),
            movesValue(statistics.successfulMoves),
        )
        ResultSummaryRow(stringResource(Res.string.total_merges), mergesValue(statistics.totalMerges))
    }
}

@Composable
internal fun gamesValue(value: Long): String = pluralStringResource(
    Res.plurals.games_count,
    value.pluralQuantity(),
    value,
)

@Composable
internal fun movesValue(value: Long): String = pluralStringResource(
    Res.plurals.moves_count,
    value.pluralQuantity(),
    value,
)

@Composable
internal fun mergesValue(value: Long): String = pluralStringResource(
    Res.plurals.merges_count,
    value.pluralQuantity(),
    value,
)

@Composable
internal fun undoUsesValue(value: Long): String = pluralStringResource(
    Res.plurals.undo_uses_count,
    value.pluralQuantity(),
    value,
)

private fun Long.pluralQuantity(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
