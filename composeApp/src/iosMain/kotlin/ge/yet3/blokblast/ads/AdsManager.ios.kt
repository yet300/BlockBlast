package ge.yet3.blokblast.ads

actual object AdsManager {
    private var adsEnabled: Boolean = false

    actual val enabled: Boolean
        get() = adsEnabled

    actual fun setEnabled(enabled: Boolean) {
        if (adsEnabled == enabled) return
        adsEnabled = enabled
        IosAdBridge.adsEnabledChanged?.invoke(enabled)
        if (enabled) requestConsentAndAds()
    }

    actual fun requestConsentAndAds() {
        if (!adsEnabled) return
        IosAdBridge.requestConsentAndAds?.invoke()
    }
}
