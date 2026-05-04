package ge.yet3.blokblast.ads

import platform.UIKit.UIView

/**
 * Bridge between Kotlin/Native and the Swift AdMob coordinator.
 *
 * The Swift side (`iosApp/iosApp/AdCoordinator.swift`) sets these lambdas
 * during app launch, before any Compose screen reads them. Kotlin code then
 * invokes them from the iOS actual implementations of [AdBanner] and
 * [rememberGameOverInterstitial].
 *
 * All callbacks are optional — if the Swift side hasn't wired them (e.g.
 * during Kotlin-side previews or tests), the Kotlin actuals fall back to
 * sensible no-ops.
 */
object IosAdBridge {
    /** Starts the iOS consent / ATT flow after Swift has a visible presenter. */
    var requestConsentAndAds: (() -> Unit)? = null

    /** Preloads an interstitial. Called once on app launch. */
    var loadInterstitial: (() -> Unit)? = null

    /**
     * Presents the cached interstitial, invoking [onDismiss] after the ad is
     * dismissed (or after failure to present). Implementations MUST always
     * eventually call [onDismiss] so the post-ad flow (e.g. revive) proceeds.
     */
    var showInterstitial: ((onDismiss: () -> Unit) -> Unit)? = null

    /**
     * Factory that returns a GADBannerView configured for the given unit ID.
     * Wrapped by Kotlin in a `UIKitView` for placement in Compose layouts.
     */
    var makeBannerView: ((adUnitId: String) -> UIView)? = null
}
