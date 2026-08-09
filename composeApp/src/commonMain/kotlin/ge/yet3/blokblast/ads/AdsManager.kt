package ge.yet3.blokblast.ads

expect object AdsManager {
    val enabled: Boolean

    fun setEnabled(enabled: Boolean)

    fun requestConsentAndAds()
}

internal fun shouldRequestAds(
    preferenceEnabled: Boolean,
    consentAllowsRequests: Boolean,
): Boolean = preferenceEnabled && consentAllowsRequests
