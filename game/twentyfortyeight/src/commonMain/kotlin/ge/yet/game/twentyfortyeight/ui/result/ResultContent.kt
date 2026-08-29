package ge.yet.game.twentyfortyeight.ui.result

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import ge.yet.game.twentyfortyeight.generated.resources.highest_tile
import ge.yet.game.twentyfortyeight.generated.resources.new_game
import ge.yet.game.twentyfortyeight.store.UiErrorCode
import ge.yet.game.twentyfortyeight.ui.common.errorText
import ge.yet.game.twentyfortyeight.ui.common.movesValue
import ge.yet.game.twentyfortyeight.ui.motion.MotionPolicy
import ge.yet.game.twentyfortyeight.ui.motion.finiteEntryReveal
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.components.icon.Crown
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ResultContent(
    model: ResultComponent.Model,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier,
    error: UiErrorCode? = null,
    resultFocusRequester: FocusRequester? = null,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("result_viewport"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .testTag("result_card")
                    .finiteEntryReveal(MotionPolicy.Normal.gameOverMs),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
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
                    Text(
                        text = model.score.formatScore(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ResultMetric(
                            label = stringResource(Res.string.best),
                            value = model.bestScore.formatScore(),
                            showCrown = true,
                            modifier = Modifier.weight(1f),
                        )
                        ResultMetric(
                            label = stringResource(Res.string.highest_tile),
                            value = model.highestTile.formatScore(),
                            modifier = Modifier.weight(1f),
                        )
                    }
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
        }
    }
}

@Composable
private fun ResultMetric(
    label: String,
    value: String,
    showCrown: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (showCrown) {
                Icon(
                    imageVector = Crown,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.tertiary,
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
