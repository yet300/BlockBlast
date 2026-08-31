package ge.yet.game.fruitmerge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.arkivanov.decompose.value.Value
import ge.yet.game.fruitmerge.session.FruitMergeSessionComponent
import ge.yet.game.fruitmerge.ui.FruitMergeTestTags
import ge.yet.game.fruitmerge.ui.MarketPriceTag
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
    override fun TopBarContent() {
        val model by component.game.model.subscribeAsState()
        if (model.initialized) {
            MarketPriceTag(
                score = model.game.score,
                bestScore = model.game.bestScore,
                bestImprovedInRun = model.game.bestImprovedInRun,
                modifier = Modifier.semantics { testTag = FruitMergeTestTags.PriceTag },
            )
        }
    }

    @Composable
    override fun Background(modifier: Modifier) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.background))
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
    MARKET,
}

internal fun fruitMergeBackgroundRole(@Suppress("UNUSED_PARAMETER") mode: MiniAppFrameMode):
    FruitMergeBackgroundRole = FruitMergeBackgroundRole.MARKET
