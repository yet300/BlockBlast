package ge.yet.game.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.feature.catalog.ui.CatalogContent
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.monetization.ads.LocalMonetizationState
import ge.yet.game.monetization.ads.AdBanner
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.screen.miniapp.MiniAppFrame
import ge.yet.game.screen.miniapp.MiniAppUnavailableContent
import ge.yet.game.uikit.components.background.AmbientMeshBackground
import ge.yet.game.utils.cupertinoPredictiveBackAnimation

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(
    component: RootComponent,
    modifier: Modifier = Modifier,
) {
    val childStack by component.stack.subscribeAsState()
    RootHost(modifier = modifier) {
        Children(
            modifier = Modifier.fillMaxSize(),
            stack = childStack,
            animation = cupertinoPredictiveBackAnimation(
                backHandler = component.backHandler,
                onBack = component::onBackClicked,
            ),
        ) { child ->
            RootChildContent(
                child = child.instance,
                onBack = component::onBackClicked,
                onSettings = component::onSettingsClicked,
            )
        }
        RootSheet(component = component)
    }
}

@Composable
internal fun RootHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AmbientMeshBackground(
            modifier = Modifier
                .fillMaxSize()
                .testTag("root_ambient_background"),
            baseColor = MaterialTheme.colorScheme.background,
        )
        content()
    }
}

@Composable
internal fun RootChildContent(
    child: RootComponent.Child,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: (@Composable () -> Unit)? = null,
) {
    when (child) {
        is RootComponent.Child.Catalog -> CatalogContent(
            component = child.component,
            modifier = modifier.fillMaxSize(),
        )

        is RootComponent.Child.RunningMiniApp -> {
            val session = (child.state as? RootComponent.MiniAppState.Content)?.session
            val frameMode = session?.frameMode?.subscribeAsState()?.value
                ?: MiniAppFrameMode.Standard

            MiniAppFrame(
                onBack = onBack,
                onSettings = onSettings,
                frameMode = frameMode,
                modifier = modifier,
                topBar = { session?.TopBarContent() },
                bottomBar = bottomBar ?: if (LocalMonetizationState.current.canShowAds) {
                    { AdBanner() }
                } else {
                    null
                },
            ) { viewport ->
                when (val state = child.state) {
                    is RootComponent.MiniAppState.Content -> state.session.Content(viewport)
                    is RootComponent.MiniAppState.Unavailable -> MiniAppUnavailableContent(
                        id = state.id,
                        onBack = onBack,
                        modifier = viewport,
                    )
                }
            }
        }
    }
}
