package ge.yet3.blokblast.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.app.common.config.AppConfig
import com.google.android.gms.ads.MobileAds

@Composable
actual fun rememberGameOverInterstitial(): GameOverInterstitial {
    val context = LocalContext.current
    val manager = remember {
        InterstitialAdManager(AppConfig.GAME_OVER_INTERSTITIAL_UNIT_ID_ANDROID)
    }

    // One-time MobileAds init per process; safe to call repeatedly.
    LaunchedEffect(Unit) {
        MobileAds.initialize(context.applicationContext) { /* no-op */ }
        manager.load(context)
    }

    return remember(manager) {
        GameOverInterstitial(
            show = { onDismiss ->
                val activity = context.findActivity()
                if (activity == null) {
                    onDismiss()
                } else {
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
