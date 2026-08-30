package ge.yet.game.fruitmerge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import ge.yet.game.fruitmerge.session.FruitMergeSessionComponent
import ge.yet.game.miniapp.compose.MiniAppFrameMode
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.miniapp.compose.MiniAppSession

class FruitMergeSession internal constructor(
    private val component: FruitMergeSessionComponent,
    private val interstitials: MiniAppInterstitialCapability,
) : MiniAppSession {
    override val frameMode: Value<MiniAppFrameMode> = component.frameMode

    override fun handleBack(): Boolean = component.handleBack()

    @Composable
    override fun Background(modifier: Modifier) {
        val mode by frameMode.subscribeAsState()
        val color = when (fruitMergeBackgroundRole(mode)) {
            FruitMergeBackgroundRole.PLAYING -> MaterialTheme.colorScheme.background
            FruitMergeBackgroundRole.RESULT -> MaterialTheme.colorScheme.primaryContainer
        }
        Box(modifier = modifier.background(color))
    }

    @Composable
    override fun Content(modifier: Modifier) {
        val clearGate = interstitials.rememberGate(MiniAppInterstitialPlacement.FRUIT_MERGE_CLEAR)
        val shakeGate = interstitials.rememberGate(MiniAppInterstitialPlacement.FRUIT_MERGE_SHAKE)
        FruitMergeContent(
            component = component,
            requestClearAd = { token ->
                clearGate.request { component.completePaidAction(token) }
            },
            requestShakeAd = { token ->
                shakeGate.request { component.completePaidAction(token) }
            },
            modifier = modifier,
        )
    }
}

internal enum class FruitMergeBackgroundRole {
    PLAYING,
    RESULT,
}

internal fun fruitMergeBackgroundRole(mode: MiniAppFrameMode): FruitMergeBackgroundRole = when (mode) {
    MiniAppFrameMode.Standard -> FruitMergeBackgroundRole.PLAYING
    MiniAppFrameMode.ContentOnly -> FruitMergeBackgroundRole.RESULT
}
