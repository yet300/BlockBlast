package ge.yet3.blokblast.ads

import app.lexilabs.basic.ads.AdState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdsPolicyTest {

    @Test
    fun ads_can_be_requested_only_when_preference_and_consent_allow_them() {
        assertTrue(shouldRequestAds(preferenceEnabled = true, consentAllowsRequests = true))
        assertFalse(shouldRequestAds(preferenceEnabled = false, consentAllowsRequests = true))
        assertFalse(shouldRequestAds(preferenceEnabled = true, consentAllowsRequests = false))
        assertFalse(shouldRequestAds(preferenceEnabled = false, consentAllowsRequests = false))
    }

    @Test
    fun interstitial_can_be_shown_only_when_ads_are_enabled_and_ad_is_ready() {
        assertTrue(shouldShowInterstitial(adsEnabled = true, adState = AdState.READY))

        AdState.entries
            .filterNot { it == AdState.READY }
            .forEach { state ->
                assertFalse(shouldShowInterstitial(adsEnabled = true, adState = state))
            }

        assertFalse(shouldShowInterstitial(adsEnabled = false, adState = AdState.READY))
    }

    @Test
    fun completion_callback_is_invoked_at_most_once() {
        var invocations = 0
        val complete = once { invocations += 1 }

        complete()
        complete()

        assertEquals(1, invocations)
    }
}
