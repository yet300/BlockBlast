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
import AppTrackingTransparency

@MainActor
final class ConsentManager {
    static let shared = ConsentManager()

    private var mobileAdsInitialized = false
    private var readyFired = false
    private var gatherStarted = false
    private var attFlowStarted = false
    private var didBecomeActiveObserver: NSObjectProtocol?
    private var attRetryCount = 0

    private init() {}

    /// Whether AdMob requests are currently permitted.
    /// UMP caches consent across launches, so this may return `true`
    /// immediately on relaunch even before `gather` completes.
    var canRequestAds: Bool {
        ConsentInformation.shared.canRequestAds
    }

    /// Request ATT first, then request UMP consent info, show the form if
    /// required, and start the AdMob SDK only after both gates complete. Call
    /// after the app UI is visible, for example from the root controller's
    /// first `viewDidAppear`.
    ///
    /// `onReadyToRequestAds` fires exactly once per process — on the first
    /// transition to "can request ads".
    func gather(
        from rootViewController: UIViewController?,
        onReadyToRequestAds: @escaping () -> Void
    ) {
        guard !gatherStarted else { return }
        gatherStarted = true

        requestATTIfNeeded(from: rootViewController) { [weak self, weak rootViewController] in
            guard let self else { return }
            self.gatherUMPConsent(from: rootViewController, onReadyToRequestAds: onReadyToRequestAds)
        }
    }

    private func gatherUMPConsent(
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
                if self.canRequestAds {
                    self.fireReady(onReadyToRequestAds)
                }
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

        // Already-cached consent path. ATT has completed before this method is
        // called, so ads may start as soon as UMP permits requests.
        if canRequestAds {
            fireReady(onReadyToRequestAds)
        }
    }

    private func requestATTIfNeeded(
        from presenter: UIViewController?,
        completion: @escaping () -> Void
    ) {
        guard !attFlowStarted else { return }
        attFlowStarted = true

        print("[ConsentManager] ATT flow requested. currentStatus=\(Self.attStatusDescription)")
        scheduleATTRequestAfterUISettles(from: presenter, completion: completion)
    }

    private func scheduleATTRequestAfterUISettles(
        from presenter: UIViewController?,
        completion: @escaping () -> Void
    ) {
        let delay: TimeInterval = attRetryCount == 0 ? 1.0 : 0.35
        DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self, weak presenter] in
            guard let self else { return }

            guard self.isReadyToRequestATT() else {
                self.attRetryCount += 1
                print(
                    "[ConsentManager] ATT deferred. appState=\(UIApplication.shared.applicationState.rawValue), " +
                    "hasWindow=\(presenter?.view.window != nil), retry=\(self.attRetryCount)"
                )
                if self.attRetryCount < 8 {
                    let retry: () -> Void = { [weak self, weak presenter] in
                        guard let self else { return }
                        self.scheduleATTRequestAfterUISettles(from: presenter, completion: completion)
                    }
                    if UIApplication.shared.applicationState == .active {
                        retry()
                    } else {
                        self.runWhenAppBecomesActive(retry)
                    }
                } else {
                    print("[ConsentManager] ATT skipped because UI never became ready.")
                    completion()
                }
                return
            }

            self.performATTIfNeeded(completion: completion)
        }
    }

    private func performATTIfNeeded(completion: @escaping () -> Void) {
        let status = ATTrackingManager.trackingAuthorizationStatus
        print("[ConsentManager] ATT request point reached. status=\(Self.attStatusDescription)")

        guard status == .notDetermined else {
            completion()
            return
        }

        ATTrackingManager.requestTrackingAuthorization { status in
            DispatchQueue.main.async {
                print("[ConsentManager] ATT completed. status=\(Self.attStatusDescription(status))")
                completion()
            }
        }
    }

    private func isReadyToRequestATT() -> Bool {
        UIApplication.shared.applicationState == .active
    }

    private func runWhenAppBecomesActive(_ block: @escaping () -> Void) {
        guard didBecomeActiveObserver == nil else { return }
        didBecomeActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            guard let self else { return }
            if let observer = self.didBecomeActiveObserver {
                NotificationCenter.default.removeObserver(observer)
                self.didBecomeActiveObserver = nil
            }
            block()
        }
    }

    private static var attStatusDescription: String {
        attStatusDescription(ATTrackingManager.trackingAuthorizationStatus)
    }

    private static func attStatusDescription(_ status: ATTrackingManager.AuthorizationStatus) -> String {
        switch status {
        case .notDetermined:
            return "notDetermined"
        case .restricted:
            return "restricted"
        case .denied:
            return "denied"
        case .authorized:
            return "authorized"
        @unknown default:
            return "unknown"
        }
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
