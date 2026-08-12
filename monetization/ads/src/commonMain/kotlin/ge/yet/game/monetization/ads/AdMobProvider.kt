package ge.yet.game.monetization.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import app.lexilabs.basic.ads.BasicAds
import app.lexilabs.basic.ads.DependsOnGoogleMobileAds
import app.lexilabs.basic.ads.DependsOnGoogleUserMessagingPlatform
import ge.yet.game.monetization.core.MonetizationEntitlement
import ge.yet.game.monetization.core.MonetizationState

val LocalMonetizationState = staticCompositionLocalOf {
    MonetizationState(
        adsPreferenceEnabled = false,
        consentAllowsAds = false,
        entitlement = MonetizationEntitlement.FREE,
    )
}

internal val LocalAdMobConfiguration =
    staticCompositionLocalOf<AdMobConfiguration?> { null }

@OptIn(DependsOnGoogleUserMessagingPlatform::class)
@Composable
fun rememberAdMobState(
    preferenceEnabled: Boolean,
    entitlement: MonetizationEntitlement,
): MonetizationState {
    val adsRequested =
        preferenceEnabled && entitlement != MonetizationEntitlement.AD_FREE
    val consentAllowsAds = rememberAdsConsentAllowsRequests(adsRequested)
    return MonetizationState(
        adsPreferenceEnabled = preferenceEnabled,
        consentAllowsAds = consentAllowsAds,
        entitlement = entitlement,
    )
}

@OptIn(DependsOnGoogleMobileAds::class)
@Composable
fun AdMobProvider(
    state: MonetizationState,
    configuration: AdMobConfiguration,
    content: @Composable () -> Unit,
) {
    if (state.canShowAds) {
        BasicAds.Initialize()
    }
    CompositionLocalProvider(
        LocalMonetizationState provides state,
        LocalAdMobConfiguration provides configuration,
        content = content,
    )
}
