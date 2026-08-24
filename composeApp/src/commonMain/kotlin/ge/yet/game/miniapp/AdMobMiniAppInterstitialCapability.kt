package ge.yet.game.miniapp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import ge.yet.game.miniapp.compose.MiniAppInterstitialCapability
import ge.yet.game.miniapp.compose.MiniAppInterstitialGate
import ge.yet.game.miniapp.compose.MiniAppInterstitialPlacement
import ge.yet.game.monetization.ads.LocalMonetizationState
import ge.yet.game.monetization.ads.rememberGameOverInterstitial
import ge.yet.game.monetization.core.once

@SingleIn(AppScope::class)
@Inject
internal class AdMobMiniAppInterstitialCapability : MiniAppInterstitialCapability {
    @Composable
    override fun rememberGate(
        placement: MiniAppInterstitialPlacement,
    ): MiniAppInterstitialGate {
        require(placement == MiniAppInterstitialPlacement.CONTINUE_AFTER_GAME_OVER)
        val state = LocalMonetizationState.current
        val presenter = rememberGameOverInterstitial()
        return remember(state.canShowAds, presenter) {
            miniAppInterstitialGate(state.canShowAds, presenter)
        }
    }
}

internal fun miniAppInterstitialGate(
    canShowAds: Boolean,
    presenter: (onComplete: () -> Unit) -> Unit,
): MiniAppInterstitialGate = MiniAppInterstitialGate(
    willShowAd = canShowAds,
    request = { completion ->
        val completeOnce = once(completion)
        if (canShowAds) presenter(completeOnce) else completeOnce()
    },
)
