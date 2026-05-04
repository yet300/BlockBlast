package ge.yet3.blokblast.ads

actual object AdsManager {
    actual fun requestConsentAndAds() {
        IosAdBridge.requestConsentAndAds?.invoke()
    }
}
