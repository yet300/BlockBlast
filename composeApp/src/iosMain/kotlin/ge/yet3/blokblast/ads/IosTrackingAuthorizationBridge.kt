package ge.yet3.blokblast.ads

import ge.yet.blockblast.monetization.ads.AdMobTrackingAuthorizationBridge

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
