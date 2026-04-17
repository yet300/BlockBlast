package ge.yet3.blokblast.ads

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Wraps a [com.google.android.gms.ads.AdView] inside a 50dp-tall [Box]. Height
 * is reserved before the ad loads so surrounding layout doesn't jump.
 */
@Composable
fun AdMobBanner(
    adUnitId: String,
    modifier: Modifier = Modifier,
) {
    val adView = remember { mutableAdViewHolder() }

    DisposableEffect(adUnitId) {
        onDispose {
            adView.value?.destroy()
            adView.value = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
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
                    loadAd(AdRequest.Builder().build())
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
