package ge.yet3.blokblast.screen.settings.content

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.open_source_libraries
import ge.yet.blockblast.feature.settings.libraries.LibrariesSettingsComponent
import ge.yet3.blokblast.component.icon.OpenInNew
import ge.yet3.blokblast.screen.settings.SettingsDivider
import ge.yet3.blokblast.screen.settings.SettingsHeader
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibrariesSettingsContent(component: LibrariesSettingsComponent) {
    val uriHandler = LocalUriHandler.current

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(Res.string.open_source_libraries),
                onBackClicked = component::onBackClicked,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        items(
            items = component.libraries,
            key = { it.url },
        ) { lib ->
            LibraryRow(
                library = lib,
                onClick = { uriHandler.openUri(lib.url) },
            )
            SettingsDivider()
        }
    }
}

@Composable
private fun LibraryRow(
    library: LibrariesSettingsComponent.Library,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = library.name,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = library.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}
