package ge.yet.game.feature.catalog.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.catalog.PreviewCatalogComponent
import ge.yet.game.feature.catalog.generated.resources.Res
import ge.yet.game.feature.catalog.generated.resources.play
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.uikit.components.button.PrimaryTerracottaButton
import ge.yet.game.uikit.theme.LogicaTheme
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun CatalogContent(
    component: CatalogComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()

    CatalogContent(
        model = model,
        onPlay = component::onPlayClicked,
        modifier = modifier,
    )
}

@Composable
private fun CatalogContent(
    model: CatalogComponent.Model,
    onPlay: (MiniAppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val padding = catalogContentPadding(
        contentPadding = 20.dp,
        safeTop = with(density) { safeDrawing.getTop(this).toDp() },
        safeBottom = with(density) { safeDrawing.getBottom(this).toDp() },
        safeStart = with(density) {
            val pixels = if (layoutDirection == LayoutDirection.Ltr) {
                safeDrawing.getLeft(this, layoutDirection)
            } else {
                safeDrawing.getRight(this, layoutDirection)
            }
            pixels.toDp()
        },
        safeEnd = with(density) {
            val pixels = if (layoutDirection == LayoutDirection.Ltr) {
                safeDrawing.getRight(this, layoutDirection)
            } else {
                safeDrawing.getLeft(this, layoutDirection)
            }
            pixels.toDp()
        },
    )
    LazyColumn(
        modifier = modifier.testTag("catalog_content"),
        contentPadding = PaddingValues(
            start = padding.start,
            top = padding.top,
            end = padding.end,
            bottom = padding.bottom,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(
            items = model.manifests,
            key = { it.id.value },
        ) { manifest ->
            MiniAppCard(
                manifest = manifest,
                onPlay = { onPlay(manifest.id) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MiniAppCard(
    manifest: MiniAppManifest,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.testTag("catalog_card_${manifest.id.value}"),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(manifest.icon),
                contentDescription = null,
                modifier = Modifier.size(72.dp),
            )
            Text(
                text = stringResource(manifest.title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(manifest.description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PrimaryTerracottaButton(
                text = stringResource(Res.string.play),
                onClick = onPlay,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("catalog_play_${manifest.id.value}"),
            )
        }
    }
}

@PreviewLightDark
@Composable
fun CatalogContentPreview() = LogicaTheme {
    CatalogContent(
        modifier = Modifier.fillMaxSize(),
        component = PreviewCatalogComponent()
    )
}
