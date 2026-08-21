package ge.yet.game.screen.miniapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.back_to_catalog
import blockblast.composeapp.generated.resources.miniapp_unavailable
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import org.jetbrains.compose.resources.stringResource

@Composable
fun MiniAppUnavailableContent(
    id: MiniAppId,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.miniapp_unavailable, id.value),
            style = MaterialTheme.typography.titleLarge,
        )
        PrimaryTerracottaButton(
            text = stringResource(Res.string.back_to_catalog),
            onClick = onBack,
        )
    }
}
