package ge.yet.game.monetization.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdsPolicyTest {

    @Test
    fun ads_require_preference_consent_and_free_entitlement() {
        assertTrue(MonetizationState(true, true, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(false, true, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(true, false, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(false, false, MonetizationEntitlement.FREE).canShowAds)
        assertFalse(MonetizationState(true, true, MonetizationEntitlement.AD_FREE).canShowAds)
    }

    @Test
    fun interstitial_requires_permission_and_readiness() {
        assertTrue(shouldShowInterstitial(adsAllowed = true, isReady = true))
        assertFalse(shouldShowInterstitial(adsAllowed = false, isReady = true))
        assertFalse(shouldShowInterstitial(adsAllowed = true, isReady = false))
        assertFalse(shouldShowInterstitial(adsAllowed = false, isReady = false))
    }

    @Test
    fun completion_runs_once() {
        var calls = 0
        val complete = once { calls++ }

        complete()
        complete()

        assertEquals(1, calls)
    }
}
