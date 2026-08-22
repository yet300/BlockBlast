package ge.yet.game.feature.catalog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ge.yet.game.feature.catalog.generated.resources.Res
import ge.yet.game.feature.catalog.generated.resources.play
import ge.yet.game.miniapp.compose.MiniAppManifest
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MiniAppListItemCard(
    manifest: MiniAppManifest,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag("catalog_card_${manifest.id.value}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        ListItem(
            modifier = Modifier.heightIn(min = 88.dp),
            headlineContent = {
                Text(
                    text = stringResource(manifest.title),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(manifest.description),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Image(
                    painter = painterResource(manifest.icon),
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .testTag("catalog_icon_${manifest.id.value}"),
                )
            },
            trailingContent = {
                Button(
                    onClick = onPlay,
                    modifier = Modifier
                        .heightIn(min = 40.dp)
                        .testTag("catalog_play_${manifest.id.value}"),
                ) {
                    Text(stringResource(Res.string.play))
                }
            },
        )
    }
}
