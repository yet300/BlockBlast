package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

@Composable
actual fun rememberGameOverInterstitial(): GameOverInterstitial {
    // Kick a preload on first composition so the Swift coordinator has an ad
    // cached by the time the user hits Game Over.
    LaunchedEffect(Unit) { IosAdBridge.loadInterstitial?.invoke() }

    return remember {
        GameOverInterstitial(
            show = { onDismiss ->
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
