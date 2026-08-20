package ge.yet.game.screen.root

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ExperimentalDecomposeApi
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.blockblast.component.result.GameResultComponent
import ge.yet.game.blockblast.ui.game.BlockBlastGameContent
import ge.yet.game.blockblast.ui.result.GameResultContent
import ge.yet.game.feature.root.RootComponent
import ge.yet.game.miniapp.miniAppInterstitialGate
import ge.yet.game.monetization.ads.LocalMonetizationState
import ge.yet.game.monetization.ads.rememberGameOverInterstitial
import ge.yet.game.screen.home.HomeContent
import ge.yet.game.utils.cupertinoPredictiveBackAnimation

@OptIn(ExperimentalDecomposeApi::class)
@Composable
fun RootContent(
    modifier: Modifier = Modifier,
    component: RootComponent
) {
    val childStack by component.stack.subscribeAsState()
    Children(
        modifier = modifier,
        stack = childStack,
        animation = cupertinoPredictiveBackAnimation(
            backHandler = component.backHandler,
            onBack = component::onBackClicked,
        ),
    ) { child ->
        when (val instance = child.instance) {
            is RootComponent.Child.Game -> BlockBlastGameContent(component = instance.component)
            is RootComponent.Child.Home -> HomeContent(component = instance.component)
            is RootComponent.Child.Result -> LegacyGameResultContent(
                component = instance.component,
            )
        }
    }
    RootSheet(component = component)
}

@Composable
private fun LegacyGameResultContent(
    component: GameResultComponent,
    modifier: Modifier = Modifier,
) {
    val presenter = rememberGameOverInterstitial()
    val gate = miniAppInterstitialGate(
        canShowAds = LocalMonetizationState.current.canShowAds,
        presenter = presenter,
    )
    GameResultContent(
        component = component,
        interstitialGate = gate,
        modifier = modifier,
    )
}
