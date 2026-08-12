package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.rememberInterstitialAd
import com.app.common.config.AppConfig
import ge.yet3.blokblast.component.utils.LocalAdsEnabled

/**
 * Uses basic-ads for cross-platform AdMob interstitial support.
 *
 * Returns a function that shows the interstitial and calls [onDismiss] when done.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun rememberGameOverInterstitial(): (onDismiss: () -> Unit) -> Unit {
    val adsEnabled = LocalAdsEnabled.current
    if (!adsEnabled) {
        return remember { { onDismiss -> onDismiss() } }
    }

    val adUnitId = AdUnitId.autoSelect(
        androidAdUnitId = AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_ANDROID,
        iosAdUnitId = AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_IOS,
    )
    val interstitialAd by rememberInterstitialAd(adUnitId = adUnitId)

    return remember(interstitialAd, adUnitId) {
        { onDismiss ->
            if (!shouldShowInterstitial(adsEnabled = true, adState = interstitialAd.state)) {
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
