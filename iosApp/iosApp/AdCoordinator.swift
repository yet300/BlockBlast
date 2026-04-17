//
//  AdCoordinator.swift
//  Block Blast
//
//  Bridges Swift AdMob (Google Mobile Ads SDK) to the Kotlin `IosAdBridge`
//  singleton so commonMain Compose code can request banner / interstitial ads
//  without taking a direct SDK dependency.
//
//  Add the SDK via Xcode → File → Add Package Dependencies:
//      https://github.com/googleads/swift-package-manager-google-mobile-ads.git
//
//  Info.plist must include:
//      <key>GADApplicationIdentifier</key>
//      <string>ca-app-pub-3940256099942544~1458002511</string>  // TEST — replace for prod
//

import Foundation
import UIKit
import GoogleMobileAds
import ComposeApp

@MainActor
final class AdCoordinator: NSObject, FullScreenContentDelegate {
    static let shared = AdCoordinator()

    // TODO: replace with production ad unit IDs before release.
    // These are Google-provided TEST IDs for iOS.
    private let interstitialUnitId = "ca-app-pub-3940256099942544/4411468910"
    // Banner unit ID comes from Kotlin via IosAdBridge.makeBannerView(adUnitId:).

    private var interstitial: InterstitialAd?
    private var pendingDismissCallback: (() -> Void)?

    /// Installs the three bridge callbacks on the Kotlin singleton. Call once,
    /// after `MobileAds.shared.start(...)`, before any Compose screen is shown.
    func configureBridge() {
        // Bridging asymmetry to be aware of:
        //   - Assigning a Swift closure into a Kotlin `(() -> Unit)?` property
        //     setter: Swift side wants `() -> Void`, so don't return anything.
        //   - A Kotlin `() -> Unit` received as a PARAMETER (e.g. `onDismiss`)
        //     arrives typed as `() -> KotlinUnit`, so wrap it before handing
        //     it to Swift APIs that expect `() -> Void`.
        IosAdBridge.shared.loadInterstitial = { [weak self] in
            Task { @MainActor in await self?.loadInterstitial() }
        }
        IosAdBridge.shared.showInterstitial = { [weak self] onDismiss in
            let dismissAsVoid: () -> Void = { _ = onDismiss() }
            Task { @MainActor in
                self?.showInterstitial(onDismiss: dismissAsVoid)
            }
        }
        IosAdBridge.shared.makeBannerView = { [weak self] adUnitId in
            self?.makeBannerView(adUnitId: adUnitId) ?? UIView()
        }
    }

    // MARK: - Interstitial

    func loadInterstitial() async {
        do {
            interstitial = try await InterstitialAd.load(
                with: interstitialUnitId,
                request: Request()
            )
            interstitial?.fullScreenContentDelegate = self
        } catch {
            print("[AdCoordinator] interstitial load failed: \(error)")
            interstitial = nil
        }
    }

    func showInterstitial(onDismiss: @escaping () -> Void) {
        guard let ad = interstitial, let rootVC = Self.rootViewController() else {
            // No ad ready — still run the post-ad callback so revive proceeds.
            onDismiss()
            Task { @MainActor in await loadInterstitial() }
            return
        }
        pendingDismissCallback = onDismiss
        ad.present(from: rootVC)
    }

    // MARK: - Banner

    func makeBannerView(adUnitId: String) -> UIView {
        let bannerView = BannerView(adSize: AdSizeBanner)
        bannerView.adUnitID = adUnitId
        bannerView.rootViewController = Self.rootViewController()
        bannerView.load(Request())
        return bannerView
    }

    // MARK: - FullScreenContentDelegate

    func adDidDismissFullScreenContent(_ ad: FullScreenPresentingAd) {
        firePendingCallbackAndReload()
    }

    func ad(
        _ ad: FullScreenPresentingAd,
        didFailToPresentFullScreenContentWithError error: Error
    ) {
        print("[AdCoordinator] interstitial failed to present: \(error)")
        firePendingCallbackAndReload()
    }

    private func firePendingCallbackAndReload() {
        let cb = pendingDismissCallback
        pendingDismissCallback = nil
        interstitial = nil
        Task { @MainActor in await loadInterstitial() }
        cb?()
    }

    // MARK: - Helpers

    private static func rootViewController() -> UIViewController? {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first(where: { $0.isKeyWindow })?
            .rootViewController
    }
}
