package ge.yet3.blokblast.ads

import kotlin.test.Test
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
}
