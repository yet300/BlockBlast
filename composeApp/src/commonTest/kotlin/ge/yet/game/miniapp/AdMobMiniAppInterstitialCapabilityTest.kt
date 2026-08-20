package ge.yet.game.miniapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AdMobMiniAppInterstitialCapabilityTest {

    @Test
    fun `ineligible ads complete without presenting`() {
        var completions = 0
        val gate = miniAppInterstitialGate(
            canShowAds = false,
            presenter = { error("presenter must not run") },
        )

        gate.request { completions += 1 }

        assertFalse(gate.willShowAd)
        assertEquals(1, completions)
    }

    @Test
    fun `eligible ads delegate and preserve exactly once completion`() {
        var sdkCompletion: (() -> Unit)? = null
        var completions = 0
        val gate = miniAppInterstitialGate(true) { sdkCompletion = it }

        gate.request { completions += 1 }
        val completion = assertNotNull(sdkCompletion)
        completion()
        completion()

        assertTrue(gate.willShowAd)
        assertEquals(1, completions)
    }
}
