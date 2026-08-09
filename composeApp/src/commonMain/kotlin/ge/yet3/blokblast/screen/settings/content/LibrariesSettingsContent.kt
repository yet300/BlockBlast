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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import blockblast.composeapp.generated.resources.Res
import blockblast.composeapp.generated.resources.open_source_libraries
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.blockblast.feature.settings.libraries.LibrariesSettingsComponent
import ge.yet3.blokblast.component.icon.OpenInNew
import ge.yet3.blokblast.screen.settings.SettingsDivider
import ge.yet3.blokblast.screen.settings.SettingsHeader
import org.jetbrains.compose.resources.stringResource

@Composable
fun LibrariesSettingsContent(component: LibrariesSettingsComponent) {
    val uriHandler = LocalUriHandler.current
    val model by component.model.subscribeAsState()

    LibrariesSettingsContent(
        libraries = model.libraries,
        onBackClicked = component::onBackClicked,
        onLibraryClicked = { library ->
            library.url?.let(uriHandler::openUri)
        },
    )
}

@Composable
private fun LibrariesSettingsContent(
    libraries: List<LibrariesSettingsComponent.Library>,
    onBackClicked: () -> Unit,
    onLibraryClicked: (LibrariesSettingsComponent.Library) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        item {
            SettingsHeader(
                title = stringResource(Res.string.open_source_libraries),
                onBackClicked = onBackClicked,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }

        items(
            items = libraries,
            key = { it.id },
        ) { lib ->
            LibraryRow(
                library = lib,
                onClick = if (lib.url != null) {
                    { onLibraryClicked(lib) }
                } else {
                    null
                },
            )
            SettingsDivider()
        }
    }
}

@Composable
private fun LibraryRow(
    library: LibrariesSettingsComponent.Library,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            )
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
        if (library.url != null) {
            Icon(
                imageVector = OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
