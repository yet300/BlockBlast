package ge.yet3.blokblast.ads

import androidx.compose.runtime.Composable

/**
 * Controller returned by [rememberGameOverInterstitial].
 *
 * @property show Invokes a full-screen interstitial (if one is cached) and
 * runs [onDismiss] after the user dismisses it. If no ad is available or the
 * platform does not support ads, [onDismiss] is invoked immediately so the
 * caller's post-ad flow (e.g. revive) still proceeds.
 */
class GameOverInterstitial(
    val show: (onDismiss: () -> Unit) -> Unit,
)

/**
 * - Android: real AdMob interstitial. Preloads on first composition, reloads
 *   after each successful show.
 * - iOS: routed via `IosAdBridge` to a Swift `Coordinator`. No-op if the
 *   Swift side hasn't wired the bridge yet.
 */
@Composable
expect fun rememberGameOverInterstitial(): GameOverInterstitial
