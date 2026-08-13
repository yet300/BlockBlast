package ge.yet.game.screen.settings.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.disable_ads
import blockblast.composeapp.generated.resources.disable_ads_anyway
import blockblast.composeapp.generated.resources.disable_ads_body
import blockblast.composeapp.generated.resources.disable_ads_title
import blockblast.composeapp.generated.resources.keep_ads
import ge.yet.game.feature.settings.disableads.DisableAdsComponent
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.components.button.SecondaryWarmSandButton
import ge.yet.game.screen.settings.SettingsHeader
import org.jetbrains.compose.resources.stringResource

@Composable
fun DisableAdsContent(component: DisableAdsComponent) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SettingsHeader(
            title = stringResource(Res.string.disable_ads),
            onBackClicked = component::onBackClicked,
        )

        Spacer(Modifier.height(36.dp))

        Text(
            text = stringResource(Res.string.disable_ads_title),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(Res.string.disable_ads_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(36.dp))

        PrimaryTerracottaButton(
            text = stringResource(Res.string.keep_ads),
            onClick = component::onKeepAdsClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        SecondaryWarmSandButton(
            text = stringResource(Res.string.disable_ads_anyway),
            onClick = component::onDisableAdsClicked,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(28.dp))
    }
}
