package ge.yet3.blokblast.ads

import app.lexilabs.basic.ads.AdState

internal fun shouldRequestAds(
    preferenceEnabled: Boolean,
    consentAllowsRequests: Boolean,
): Boolean = preferenceEnabled && consentAllowsRequests

internal fun shouldShowInterstitial(
    adsEnabled: Boolean,
    adState: AdState,
): Boolean = adsEnabled && adState == AdState.READY

internal fun once(action: () -> Unit): () -> Unit {
    var invoked = false
    return {
        if (!invoked) {
            invoked = true
            action()
        }
    }
}
