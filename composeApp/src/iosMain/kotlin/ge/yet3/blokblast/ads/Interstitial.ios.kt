package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import ge.yet3.blokblast.component.utils.LocalAdsEnabled

@Composable
actual fun rememberGameOverInterstitial(): GameOverInterstitial {
    val adsEnabled = LocalAdsEnabled.current

    // Kick a preload on first composition so the Swift coordinator has an ad
    // cached by the time the user hits Game Over.
    LaunchedEffect(adsEnabled) {
        if (adsEnabled && AdsManager.enabled) {
            IosAdBridge.loadInterstitial?.invoke()
        }
    }

    return remember(adsEnabled) {
        GameOverInterstitial(
            show = { onDismiss ->
                if (!adsEnabled || !AdsManager.enabled) {
                    onDismiss()
                    return@GameOverInterstitial
                }
                val showFn = IosAdBridge.showInterstitial
                if (showFn != null) {
                    showFn(onDismiss)
                } else {
                    // Bridge not wired (e.g. tests / previews) — proceed anyway.
                    onDismiss()
                }
            },
        )
    }
}
