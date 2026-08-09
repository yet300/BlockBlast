package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.app.common.config.AppConfig
import ge.yet3.blokblast.ads.consent.ConsentManager
import ge.yet3.blokblast.theme.LocalAdsEnabled

@Composable
actual fun AdBanner(modifier: Modifier) {
    val consentAllowsRequests by ConsentManager.canRequestAdsFlow.collectAsState()
    if (
        !shouldRequestAds(
            preferenceEnabled = LocalAdsEnabled.current,
            consentAllowsRequests = consentAllowsRequests,
        )
    ) {
        return
    }

    AdMobBanner(
        adUnitId = AppConfig.BANNER_UNIT_ID_ANDROID,
        modifier = modifier,
    )
}
