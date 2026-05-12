package ge.yet3.blokblast.screen.settings.content

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.dark_theme
import blockblast.composeapp.generated.resources.dark_theme_subtitle
import blockblast.composeapp.generated.resources.more
import blockblast.composeapp.generated.resources.more_subtitle
import blockblast.composeapp.generated.resources.settings
import blockblast.composeapp.generated.resources.sound
import blockblast.composeapp.generated.resources.sound_subtitle
import blockblast.composeapp.generated.resources.vibration
import blockblast.composeapp.generated.resources.vibration_subtitle
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.settings.MainSettingsComponent
import ge.yet3.blokblast.component.icon.DarkMode
import ge.yet3.blokblast.component.icon.NotificationsActive
import ge.yet3.blokblast.component.icon.Settings
import ge.yet3.blokblast.component.icon.Vibration
import ge.yet3.blokblast.screen.settings.SettingsDivider
import ge.yet3.blokblast.screen.settings.SettingsLinkRow
import ge.yet3.blokblast.screen.settings.SettingsToggleRow
import org.jetbrains.compose.resources.stringResource

@Composable
fun MainSettingsContent(component: MainSettingsComponent) {
    val model by component.model.subscribeAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = stringResource(Res.string.settings),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(20.dp))

        SettingsToggleRow(
            icon = NotificationsActive,
            title = stringResource(Res.string.sound),
            subtitle = stringResource(Res.string.sound_subtitle),
            checked = model.soundEnabled,
            onCheckedChange = component::onSoundToggled,
        )

        SettingsDivider()

        SettingsToggleRow(
            icon = Vibration,
            title = stringResource(Res.string.vibration),
            subtitle = stringResource(Res.string.vibration_subtitle),
            checked = model.vibrationEnabled,
            onCheckedChange = component::onVibrationToggled,
        )

        SettingsDivider()

        SettingsToggleRow(
            icon = DarkMode,
            title = stringResource(Res.string.dark_theme),
            subtitle = stringResource(Res.string.dark_theme_subtitle),
            checked = model.darkTheme,
            onCheckedChange = component::onDarkThemeToggled,
        )

        SettingsDivider()

        SettingsLinkRow(
            icon = Settings,
            title = stringResource(Res.string.more),
            subtitle = stringResource(Res.string.more_subtitle),
            external = false,
            onClick = component::onMoreClicked,
        )

        Spacer(Modifier.height(28.dp))
    }
}
