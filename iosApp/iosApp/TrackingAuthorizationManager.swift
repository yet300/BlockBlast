import AppTrackingTransparency
import UIKit

/// Requests ATT after the first app UI is visible and reports completion for
/// every authorization status. UMP and Mobile Ads remain owned by basic-ads.
@MainActor
final class TrackingAuthorizationManager {
    static let shared = TrackingAuthorizationManager()

    private var callbacks: [() -> Void] = []
    private var requestStarted = false
    private var requestCompleted = false
    private var didBecomeActiveObserver: NSObjectProtocol?

    private init() {}

    func requestIfNeeded(
        from _: UIViewController,
        completion: @escaping () -> Void
    ) {
        if requestCompleted {
            completion()
            return
        }

        callbacks.append(completion)
        guard !requestStarted else { return }
        requestStarted = true
        requestWhenApplicationIsActive()
    }

    private func requestWhenApplicationIsActive() {
        guard UIApplication.shared.applicationState == .active else {
            observeApplicationActivation()
            return
        }

        // Let the first Compose frame settle before presenting a system alert.
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.75) { [weak self] in
            guard let self else { return }
            guard UIApplication.shared.applicationState == .active else {
                self.observeApplicationActivation()
                return
            }
            self.performRequest()
        }
    }

    private func performRequest() {
        guard ATTrackingManager.trackingAuthorizationStatus == .notDetermined else {
            finish()
            return
        }

        ATTrackingManager.requestTrackingAuthorization { [weak self] _ in
            DispatchQueue.main.async {
                self?.finish()
            }
        }
    }

    private func observeApplicationActivation() {
        guard didBecomeActiveObserver == nil else { return }
        didBecomeActiveObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didBecomeActiveNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            Task { @MainActor in
                guard let self else { return }
                if let observer = self.didBecomeActiveObserver {
                    NotificationCenter.default.removeObserver(observer)
                    self.didBecomeActiveObserver = nil
                }
                self.requestWhenApplicationIsActive()
            }
        }
    }

    private func finish() {
        guard !requestCompleted else { return }
        requestCompleted = true

        if let observer = didBecomeActiveObserver {
            NotificationCenter.default.removeObserver(observer)
            didBecomeActiveObserver = nil
        }

        let pendingCallbacks = callbacks
        callbacks.removeAll()
        pendingCallbacks.forEach { $0() }
    }
}
