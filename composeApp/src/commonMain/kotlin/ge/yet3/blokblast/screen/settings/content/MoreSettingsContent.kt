package ge.yet3.blokblast.screen.settings.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.github
import blockblast.composeapp.generated.resources.github_subtitle
import blockblast.composeapp.generated.resources.more
import blockblast.composeapp.generated.resources.open_source_libraries
import blockblast.composeapp.generated.resources.open_source_libraries_subtitle
import blockblast.composeapp.generated.resources.privacy_policy
import blockblast.composeapp.generated.resources.privacy_policy_subtitle
import com.app.common.config.AppConfig
import ge.yet.blockblast.feature.settings.more.MoreSettingsComponent
import ge.yet3.blokblast.component.icon.Github
import ge.yet3.blokblast.component.icon.OpenInNew
import ge.yet3.blokblast.component.icon.PrivacyTip
import ge.yet3.blokblast.screen.settings.SettingsDivider
import ge.yet3.blokblast.screen.settings.SettingsHeader
import ge.yet3.blokblast.screen.settings.SettingsLinkRow
import org.jetbrains.compose.resources.stringResource

@Composable
fun MoreSettingsContent(component: MoreSettingsComponent) {
    val uriHandler = LocalUriHandler.current

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

        SettingsLinkRow(
            icon = PrivacyTip,
            title = stringResource(Res.string.privacy_policy),
            subtitle = stringResource(Res.string.privacy_policy_subtitle),
            external = true,
            onClick = { uriHandler.openUri(AppConfig.PRIVACY_POLICY_URL) },
        )

        SettingsDivider()

        SettingsLinkRow(
            icon = Github,
            title = stringResource(Res.string.github),
            subtitle = stringResource(Res.string.github_subtitle),
            external = true,
            onClick = { uriHandler.openUri(AppConfig.GITHUB_URL) },
        )

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
