package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lexilabs.basic.ads.AdSize
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.BannerAd
import app.lexilabs.basic.ads.composable.rememberBannerAd
import com.app.common.config.AppConfig
import ge.yet3.blokblast.component.utils.LocalAdsEnabled

/**
 * Bottom banner ad slot.
 *
 * Uses basic-ads for cross-platform AdMob support.
 */
@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!LocalAdsEnabled.current) return

    val adUnitId = AdUnitId.autoSelect(
        androidAdUnitId = AppConfig.BANNER_UNIT_ID_ANDROID,
        iosAdUnitId = AppConfig.BANNER_UNIT_ID_IOS,
    )
    val bannerAd by rememberBannerAd(
        adUnitId = adUnitId,
        adSize = AdSize.BANNER,
    )
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        BannerAd(ad = bannerAd)
    }
}
