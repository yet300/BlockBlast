//
//  ConsentManager.swift
//  Block Blast
//
//  Wraps Google's User Messaging Platform (UMP) so GDPR / regional consent
//  is gathered before any AdMob request fires. Google requires that the
//  AdMob SDK be initialised *after* consent, so `MobileAds.shared.start`
//  is invoked from inside this class once `canRequestAds` is true.
//
//  Add the SDK via Xcode → File → Add Package Dependencies:
//      https://github.com/googleads/swift-package-manager-google-user-messaging-platform.git
//
//  Info.plist must include a valid `GADApplicationIdentifier`. Consent
//  debug/testing overrides can be wired inside `gather(from:onReadyToRequestAds:)`
//  via `UMPDebugSettings`.
//

import Foundation
import UIKit
import GoogleMobileAds
import UserMessagingPlatform

@MainActor
final class ConsentManager {
    static let shared = ConsentManager()

    private var mobileAdsInitialized = false
    private var readyFired = false

    private init() {}

    /// Whether AdMob requests are currently permitted.
    /// UMP caches consent across launches, so this may return `true`
    /// immediately on relaunch even before `gather` completes.
    var canRequestAds: Bool {
        ConsentInformation.shared.canRequestAds
    }

    /// Request UMP consent info, show the form if required, and start the
    /// AdMob SDK once the user can legally receive ads. Call from the
    /// AppDelegate's `didFinishLaunchingWithOptions`.
    ///
    /// `onReadyToRequestAds` fires exactly once per process — on the first
    /// transition to "can request ads".
    func gather(
        from rootViewController: UIViewController?,
        onReadyToRequestAds: @escaping () -> Void
    ) {
        let parameters = RequestParameters()
        // Debug overrides (EEA geography, test device hashes) can be set here
        // while testing localized consent UI.
        //
        // let debug = DebugSettings()
        // debug.geography = .EEA
        // debug.testDeviceIdentifiers = ["TEST-DEVICE-HASH"]
        // parameters.debugSettings = debug

        ConsentInformation.shared.requestConsentInfoUpdate(
            with: parameters
        ) { [weak self] requestError in
            guard let self else { return }
            if let requestError {
                print("[ConsentManager] info update failed: \(requestError.localizedDescription)")
                if self.canRequestAds {
                    self.fireReady(onReadyToRequestAds)
                }
                return
            }

            guard let presenter = rootViewController else {
                if self.canRequestAds { self.fireReady(onReadyToRequestAds) }
                return
            }

            ConsentForm.loadAndPresentIfRequired(from: presenter) { [weak self] formError in
                guard let self else { return }
                if let formError {
                    print("[ConsentManager] form error: \(formError.localizedDescription)")
                }
                if self.canRequestAds {
                    self.fireReady(onReadyToRequestAds)
                }
            }
        }

        // Already-cached consent path: fire immediately so the first
        // interstitial can start loading during the UMP round-trip.
        if canRequestAds { fireReady(onReadyToRequestAds) }
    }

    private func fireReady(_ onReadyToRequestAds: () -> Void) {
        if !mobileAdsInitialized {
            mobileAdsInitialized = true
            MobileAds.shared.start(completionHandler: nil)
        }
        if !readyFired {
            readyFired = true
            onReadyToRequestAds()
        }
    }
}
