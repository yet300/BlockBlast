package ge.yet.blockblast.monetization.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.lexilabs.basic.ads.AdState
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberInterstitialAd
import ge.yet.blockblast.monetization.core.once
import ge.yet.blockblast.monetization.core.shouldShowInterstitial

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun rememberGameOverInterstitial(): (onDismiss: () -> Unit) -> Unit {
    val state = LocalMonetizationState.current
    if (!state.canShowAds) {
        return remember { { onDismiss -> onDismiss() } }
    }
    val configuration = checkNotNull(LocalAdMobConfiguration.current) {
        "rememberGameOverInterstitial must be used inside AdMobProvider"
    }
    val adUnitId = AdUnitId.autoSelect(
        androidAdUnitId = configuration.gameOverInterstitialAndroidUnitId,
        iosAdUnitId = configuration.gameOverInterstitialIosUnitId,
    )
    val interstitialAd by rememberInterstitialAd(adUnitId = adUnitId)

    return remember(interstitialAd, adUnitId, state.canShowAds) {
        { onDismiss ->
            if (
                !shouldShowInterstitial(
                    adsAllowed = state.canShowAds,
                    isReady = interstitialAd.state == AdState.READY,
                )
            ) {
                onDismiss()
            } else {
                val complete = once(onDismiss)
                val reload = {
                    interstitialAd.load(
                        adUnitId = adUnitId,
                        onLoad = {},
                        onFailure = {},
                    )
                }

                try {
                    interstitialAd.setListeners(
                        onFailure = {
                            complete()
                            reload()
                        },
                        onDismissed = complete,
                    )
                    interstitialAd.show()
                } catch (_: IllegalArgumentException) {
                    complete()
                    reload()
                } catch (_: IllegalStateException) {
                    complete()
                    reload()
                }
            }
        }
    }
}
