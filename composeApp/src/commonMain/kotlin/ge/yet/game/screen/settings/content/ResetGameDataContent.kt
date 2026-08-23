package ge.yet.game.screen.settings.content

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.cancel
import blockblast.composeapp.generated.resources.clear_game_data_action
import blockblast.composeapp.generated.resources.clear_game_data_body
import blockblast.composeapp.generated.resources.clear_game_data_preferences_kept
import blockblast.composeapp.generated.resources.clear_game_data_title
import blockblast.composeapp.generated.resources.clearing_game_data
import blockblast.composeapp.generated.resources.done
import blockblast.composeapp.generated.resources.game_data_clear_failed
import blockblast.composeapp.generated.resources.game_data_cleared
import blockblast.composeapp.generated.resources.retry
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.feature.settings.reset.ResetGameDataComponent
import ge.yet.game.screen.settings.SettingsHeader
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import org.jetbrains.compose.resources.stringResource

@Composable
fun ResetGameDataContent(
    component: ResetGameDataComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SettingsHeader(
            title = stringResource(Res.string.clear_game_data_title),
            onBackClicked = component::onBackClicked,
        )
        Spacer(Modifier.height(24.dp))

        when (val status = model.status) {
            ResetGameDataComponent.Status.Confirming -> ConfirmingContent(component)
            ResetGameDataComponent.Status.Clearing -> ClearingContent()
            ResetGameDataComponent.Status.Success -> SuccessContent(component)
            is ResetGameDataComponent.Status.PartialFailure -> FailureContent(component, status)
        }
    }
}

@Composable
private fun ConfirmingContent(component: ResetGameDataComponent) {
    Text(
        text = stringResource(Res.string.clear_game_data_body),
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(Res.string.clear_game_data_preferences_kept),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    Button(
        onClick = component::onConfirmClicked,
        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("clear_game_data_confirm"),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        ),
    ) {
        Text(stringResource(Res.string.clear_game_data_action), fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))
    SecondaryWarmSandButton(
        text = stringResource(Res.string.cancel),
        onClick = component::onBackClicked,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ClearingContent() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("clear_game_data_progress"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(stringResource(Res.string.clearing_game_data), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SuccessContent(component: ResetGameDataComponent) {
    Text(stringResource(Res.string.game_data_cleared), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(24.dp))
    PrimaryTerracottaButton(
        text = stringResource(Res.string.done),
        onClick = component::onBackClicked,
        modifier = Modifier.fillMaxWidth().testTag("clear_game_data_done"),
    )
}

@Composable
private fun FailureContent(
    component: ResetGameDataComponent,
    status: ResetGameDataComponent.Status.PartialFailure,
) {
    Text(stringResource(Res.string.game_data_clear_failed), style = MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Text(
        text = status.failedMiniAppIds.map { it.value }.sorted().joinToString(separator = "\n"),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.testTag("clear_game_data_failed_ids"),
    )
    Spacer(Modifier.height(24.dp))
    PrimaryTerracottaButton(
        text = stringResource(Res.string.retry),
        onClick = component::onRetryClicked,
        modifier = Modifier.fillMaxWidth().testTag("clear_game_data_retry"),
    )
    Spacer(Modifier.height(12.dp))
    SecondaryWarmSandButton(
        text = stringResource(Res.string.done),
        onClick = component::onBackClicked,
        modifier = Modifier.fillMaxWidth(),
    )
}
