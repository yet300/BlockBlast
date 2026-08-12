package ge.yet.game.monetization.core

enum class MonetizationEntitlement {
    FREE,
    AD_FREE,
}

data class MonetizationState(
    val adsPreferenceEnabled: Boolean,
    val consentAllowsAds: Boolean,
    val entitlement: MonetizationEntitlement,
) {
    val canShowAds: Boolean
        get() = adsPreferenceEnabled &&
            consentAllowsAds &&
            entitlement != MonetizationEntitlement.AD_FREE
}
