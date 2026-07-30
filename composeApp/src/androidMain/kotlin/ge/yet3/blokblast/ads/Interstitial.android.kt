package ge.yet3.blokblast.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.app.common.config.AppConfig
import ge.yet3.blokblast.ads.consent.ConsentManager
import ge.yet3.blokblast.theme.LocalAdsEnabled

@Composable
actual fun rememberGameOverInterstitial(): GameOverInterstitial {
    val context = LocalContext.current
    val adsEnabled = LocalAdsEnabled.current
    val consentAllowsRequests by ConsentManager.canRequestAdsFlow.collectAsState()
    val manager = remember {
        InterstitialAdManager(AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_ANDROID)
    }

    // AdMob SDK init happens in `ConsentManager` once UMP permits requests.
    // Only attempt to preload after consent has been gathered — otherwise the
    // request will either be rejected or fire without a valid consent token.
    LaunchedEffect(adsEnabled, consentAllowsRequests) {
        if (
            shouldRequestAds(
                preferenceEnabled = adsEnabled,
                consentAllowsRequests = consentAllowsRequests,
            )
        ) {
            manager.load(context)
        } else {
            manager.clear()
        }
    }

    DisposableEffect(manager) {
        onDispose(manager::clear)
    }

    return remember(manager, context, adsEnabled) {
        GameOverInterstitial(
            show = { onDismiss ->
                val activity = context.findActivity()
                if (
                    activity == null ||
                    !shouldRequestAds(
                        preferenceEnabled = adsEnabled && AdsManager.enabled,
                        consentAllowsRequests = ConsentManager.canRequestAds(context),
                    )
                ) {
                    onDismiss()
                } else {
                    // Lazy load — if consent arrived after the LaunchedEffect
                    // above already ran, this primes the cache for next time.
                    val shown = manager.show(activity, onDismiss)
                    if (!shown) onDismiss()
                }
            },
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
