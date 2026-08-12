package ge.yet.blockblast.monetization.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.lexilabs.basic.ads.AdSize
import app.lexilabs.basic.ads.AdUnitId
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.composable.BannerAd
import app.lexilabs.basic.ads.composable.rememberBannerAd

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    if (!LocalMonetizationState.current.canShowAds) return
    val configuration = checkNotNull(LocalAdMobConfiguration.current) {
        "AdBanner must be used inside AdMobProvider"
    }
    val adUnitId = AdUnitId.autoSelect(
        androidAdUnitId = configuration.bannerAndroidUnitId,
        iosAdUnitId = configuration.bannerIosUnitId,
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
