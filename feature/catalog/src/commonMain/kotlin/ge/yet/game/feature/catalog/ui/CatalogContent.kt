package ge.yet.game.feature.catalog.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import ge.yet.game.feature.catalog.CatalogComponent
import ge.yet.game.feature.catalog.PreviewCatalogComponent
import ge.yet.game.feature.catalog.generated.resources.Res
import ge.yet.game.feature.catalog.generated.resources.app_name
import ge.yet.game.feature.catalog.generated.resources.catalog_empty_title
import ge.yet.game.miniapp.api.MiniAppId
import ge.yet.game.miniapp.compose.MiniAppManifest
import ge.yet.game.uikit.components.background.AmbientMeshBackground
import ge.yet.game.uikit.theme.LogicaTheme
import org.jetbrains.compose.resources.stringResource

@Composable
fun CatalogContent(
    component: CatalogComponent,
    modifier: Modifier = Modifier,
) {
    val model by component.model.subscribeAsState()

    Box(modifier = modifier) {
        AmbientMeshBackground(
            modifier = Modifier
                .fillMaxSize()
                .testTag("catalog_ambient_background"),
            baseColor = MaterialTheme.colorScheme.background,
        )
        CatalogScreen(
            manifests = model.manifests,
            onPlay = component::onPlayClicked,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CatalogScreen(
    manifests: List<MiniAppManifest>,
    onPlay: (MiniAppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hazeState = rememberHazeState()

    Scaffold(
        modifier = modifier.testTag("catalog_content"),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.app_name),
                        modifier = Modifier.testTag("catalog_title"),
                    )
                },
                modifier = Modifier
                    .hazeEffect(hazeState)
                    .testTag("catalog_top_bar"),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                ),
            )
        },
    ) { scaffoldPadding ->
        CatalogGrid(
            manifests = manifests,
            scaffoldPadding = scaffoldPadding,
            onPlay = onPlay,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        )
    }

}

@Composable
private fun CatalogGrid(
    manifests: List<MiniAppManifest>,
    scaffoldPadding: PaddingValues,
    onPlay: (MiniAppId) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val layoutDirection = LocalLayoutDirection.current
        val outerPadding = catalogOuterPadding(maxWidth)
        val padding = catalogContentPadding(
            contentPadding = outerPadding,
            safeTop = scaffoldPadding.calculateTopPadding(),
            safeBottom = scaffoldPadding.calculateBottomPadding(),
            safeStart = if (layoutDirection == LayoutDirection.Ltr) {
                scaffoldPadding.calculateLeftPadding(layoutDirection)
            } else {
                scaffoldPadding.calculateRightPadding(layoutDirection)
            },
            safeEnd = if (layoutDirection == LayoutDirection.Ltr) {
                scaffoldPadding.calculateRightPadding(layoutDirection)
            } else {
                scaffoldPadding.calculateLeftPadding(layoutDirection)
            },
        )

        if (manifests.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.catalog_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@BoxWithConstraints
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(catalogColumnCount(maxWidth)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(catalogContentWidth(maxWidth))
                .testTag("catalog_grid"),
            contentPadding = PaddingValues(
                start = padding.start,
                top = padding.top,
                end = padding.end,
                bottom = padding.bottom,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = manifests,
                key = { it.id.value },
            ) { manifest ->
                MiniAppListItemCard(
                    manifest = manifest,
                    onPlay = { onPlay(manifest.id) },
                )
            }
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
