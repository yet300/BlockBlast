package ge.yet.game.ads

import ge.yet.game.monetization.ads.AdMobTrackingAuthorizationBridge

object IosTrackingAuthorizationBridge {
    var requestAuthorization: (() -> Unit)?
        get() = AdMobTrackingAuthorizationBridge.requestAuthorization
        set(value) {
            AdMobTrackingAuthorizationBridge.requestAuthorization = value
        }

    fun markCompleted() {
        AdMobTrackingAuthorizationBridge.markCompleted()
    }
}
