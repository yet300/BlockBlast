package ge.yet3.blokblast.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * Simple stateful loader/shower for a single interstitial slot.
 *
 * Not thread-safe. All calls should be made from the main thread.
 */
class InterstitialAdManager(
    private val unitId: String,
) {
    private var cached: InterstitialAd? = null
    private var loading: Boolean = false

    fun load(context: Context) {
        if (loading || cached != null) return
        loading = true
        InterstitialAd.load(
            context.applicationContext,
            unitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    cached = ad
                    loading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    cached = null
                    loading = false
                }
            },
        )
    }

    /**
     * Shows the cached interstitial if one is ready.
     *
     * @param onDismiss invoked after the ad is dismissed (or fails to show).
     * If no ad is cached, this is NOT invoked — the caller should fall back.
     * @return `true` when the show was attempted, `false` when no ad was available.
     */
    fun show(activity: Activity, onDismiss: () -> Unit = {}): Boolean {
        val ad = cached
        if (ad == null) {
            load(activity)
            return false
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                cached = null
                load(activity)
                onDismiss()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                cached = null
                load(activity)
                onDismiss()
            }
        }
        ad.show(activity)
        cached = null
        return true
    }
}
