package ge.yet.game.screen.settings.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.advertising
import blockblast.composeapp.generated.resources.advertising_disabled_subtitle
import blockblast.composeapp.generated.resources.advertising_enabled_subtitle
import blockblast.composeapp.generated.resources.github
import blockblast.composeapp.generated.resources.github_subtitle
import blockblast.composeapp.generated.resources.more
import blockblast.composeapp.generated.resources.open_source_libraries
import blockblast.composeapp.generated.resources.open_source_libraries_subtitle
import blockblast.composeapp.generated.resources.privacy_policy
import blockblast.composeapp.generated.resources.privacy_policy_subtitle
import blockblast.composeapp.generated.resources.support_game
import blockblast.composeapp.generated.resources.support_game_subtitle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.app.common.config.AppConfig
import ge.yet.game.feature.settings.more.MoreSettingsComponent
import ge.yet.game.uikit.components.icon.Github
import ge.yet.game.uikit.components.icon.OpenInNew
import ge.yet.game.uikit.components.icon.PrivacyTip
import ge.yet.game.uikit.components.icon.Settings
import ge.yet.game.screen.settings.SettingsDivider
import ge.yet.game.screen.settings.SettingsHeader
import ge.yet.game.screen.settings.SettingsLinkRow
import ge.yet.game.screen.settings.SettingsToggleRow
import org.jetbrains.compose.resources.stringResource

@Composable
fun MoreSettingsContent(component: MoreSettingsComponent) {
    val uriHandler = LocalUriHandler.current
    val model by component.model.subscribeAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        SettingsHeader(
            title = stringResource(Res.string.more),
            onBackClicked = component::onBackClicked,
        )

        Spacer(Modifier.height(12.dp))

        SettingsToggleRow(
            icon = Settings,
            title = stringResource(Res.string.advertising),
            subtitle = stringResource(
                if (model.adsEnabled) {
                    Res.string.advertising_enabled_subtitle
                } else {
                    Res.string.advertising_disabled_subtitle
                },
            ),
            checked = model.adsEnabled,
            onCheckedChange = component::onAdsToggled,
        )

        SettingsDivider()

        SettingsLinkRow(
            icon = PrivacyTip,
            title = stringResource(Res.string.privacy_policy),
            subtitle = stringResource(Res.string.privacy_policy_subtitle),
            external = true,
            onClick = { uriHandler.openUri(AppConfig.PRIVACY_POLICY_URL) },
        )

        SettingsDivider()

        if (model.adsEnabled) {
            SettingsLinkRow(
                icon = Github,
                title = stringResource(Res.string.github),
                subtitle = stringResource(Res.string.github_subtitle),
                external = true,
                onClick = { uriHandler.openUri(AppConfig.GITHUB_URL) },
            )
        } else {
            SettingsLinkRow(
                icon = Github,
                title = stringResource(Res.string.support_game),
                subtitle = stringResource(Res.string.support_game_subtitle),
                external = true,
                onClick = {
                    component.onSupportClicked()
                    uriHandler.openUri(AppConfig.GITHUB_SUPPORT_URL)
                },
            )
        }

        SettingsDivider()

        SettingsLinkRow(
            icon = OpenInNew,
            title = stringResource(Res.string.open_source_libraries),
            subtitle = stringResource(Res.string.open_source_libraries_subtitle),
            external = false,
            onClick = component::onLibrariesClicked,
        )

        Spacer(Modifier.height(28.dp))
    }
}
