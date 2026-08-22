package ge.yet.game.blockblast.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import ge.yet.game.blockblast.session.BlockBlastSessionComponent
import ge.yet.game.blockblast.ui.game.BlockBlastGameContent
import ge.yet.game.blockblast.ui.result.GameResultContent
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.uikit.components.background.AmbientMeshBackground

@Composable
internal fun BlockBlastSessionContent(
    component: BlockBlastSessionComponent,
    interstitials: MiniAppInterstitialCapability,
    modifier: Modifier = Modifier,
) {
    val stack by component.stack.subscribeAsState()

    Children(
        stack = stack,
        modifier = modifier,
        animation = stackAnimation(fade()),
    ) { child ->
        when (val instance = child.instance) {
            is BlockBlastSessionComponent.Child.Playing -> BlockBlastGameContent(
                component = instance.component,
                modifier = Modifier,
            )

            is BlockBlastSessionComponent.Child.Result -> {
                val gate = interstitials.rememberGate(
                    MiniAppInterstitialPlacement.CONTINUE_AFTER_GAME_OVER,
                )
                GameResultContent(
                    component = instance.component,
                    interstitialGate = gate,
                    modifier = Modifier,
                )
            }
        }
    }
}

@Composable
internal fun BlockBlastSessionBackground(
    modifier: Modifier = Modifier,
) {
    AmbientMeshBackground(
        modifier = modifier.testTag("blockblast_ambient_background"),
        baseColor = MaterialTheme.colorScheme.background,
    )
}
