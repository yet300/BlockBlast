package ge.yet3.blokblast.screen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.settings.SettingsComponent
import ge.yet3.blokblast.component.icon.DarkMode
import ge.yet3.blokblast.component.icon.NotificationsActive
import ge.yet3.blokblast.component.icon.Vibration
import ge.yet3.blokblast.component.modifier.ringShadow
import org.jetbrains.compose.resources.stringResource
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.dark_theme
import blockblast.composeapp.generated.resources.dark_theme_subtitle
import blockblast.composeapp.generated.resources.settings
import blockblast.composeapp.generated.resources.sound
import blockblast.composeapp.generated.resources.sound_subtitle
import blockblast.composeapp.generated.resources.vibration
import blockblast.composeapp.generated.resources.vibration_subtitle

@Composable
fun SettingsContent(component: SettingsComponent) {
    val model by component.model.subscribeAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp, vertical = 4.dp),
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

        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            // Custom-vector icon in a small warm-sand pill
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .ringShadow(
                        color = MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(12.dp),
                    )
                    .background(
                        MaterialTheme.colorScheme.secondary,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium,
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.secondary,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline,
            ),
        )
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outline,
    )
}
