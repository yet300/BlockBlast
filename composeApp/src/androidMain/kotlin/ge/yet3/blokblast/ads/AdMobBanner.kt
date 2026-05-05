package ge.yet3.blokblast.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import ge.yet3.blokblast.ads.consent.ConsentManager

/**
 * Wraps a [com.google.android.gms.ads.AdView] inside a [Box].
 * The height is 0dp until the ad successfully loads, at which point it
 * expands to 50dp. This prevents empty gaps when offline or when no
 * ads are available.
 */
@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier,
) {
    val adView = remember { mutableAdViewHolder() }
    var isAdLoaded by remember { mutableStateOf(false) }

    DisposableEffect(adUnitId) {
        onDispose {
            adView.value?.destroy()
            adView.value = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isAdLoaded) 50.dp else 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { ctx ->
                AdView(ctx).apply {
                    setAdSize(AdSize.BANNER)
                    this.adUnitId = adUnitId
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    )

                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            isAdLoaded = true
                        }
                        override fun onAdFailedToLoad(error: com.google.android.gms.ads.LoadAdError) {
                            isAdLoaded = false
                        }
                    }

                    if (ConsentManager.canRequestAds(ctx)) {
                        loadAd(AdRequest.Builder().build())
                    }
                    adView.value = this
                }
            },
        )
    }
}

private class AdViewHolder {
    var value: AdView? = null
}

private fun mutableAdViewHolder(): AdViewHolder = AdViewHolder()
