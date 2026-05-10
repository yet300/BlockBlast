import ComposeApp
import GoogleMobileAds
import FirebaseCore
import SwiftUI

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self)
    var appDelegate: AppDelegate

    var body: some Scene {
        WindowGroup {
            ComposeView(
                root: appDelegate.root,
                backDispatcher: appDelegate.backDispatcher,
                onFirstVisible: { presenter in
                    appDelegate.startConsentFlow(from: presenter)
                }
            )
                .ignoresSafeArea(.all)
        }
    }
}


class AppDelegate: NSObject, UIApplicationDelegate {
    private var stateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: nil)
    var backDispatcher: BackDispatcher = BackDispatcherKt.BackDispatcher()

    private let appGraph = NativeAppGraphKt.getNativeAppGraph()

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Wire the Kotlin ad bridge up-front; banner creation works even
        // before an ad request fires. Google Mobile Ads is started *inside*
        // `ConsentManager.gather` once UMP confirms requests are permitted —
        // initialising earlier would violate UMP's GDPR requirements.
        FirebaseApp.configure()
        Task { @MainActor in
            AdCoordinator.shared.configureBridge()
        }
        return true
    }

    @MainActor
    func startConsentFlow(from presenter: UIViewController) {
        IosAdBridge.shared.requestConsentAndAds = { [weak self, weak presenter] in
            guard let self, let presenter else { return }
            self.startConsentFlow(from: presenter)
        }

        ConsentManager.shared.gather(from: presenter) {
            // Ads may now be requested — preload the game-over interstitial.
            Task { @MainActor in
                await AdCoordinator.shared.loadInterstitial()
            }
        }
    }

    lazy var root: RootComponent = {
        let context = DefaultComponentContext(
            lifecycle: ApplicationLifecycle(),
            stateKeeper: stateKeeper,
            instanceKeeper: nil,
            backHandler: backDispatcher
        )

        return appGraph.rootFactory.create(componentContext: context)
    }()

    func application(_ application: UIApplication, supportedInterfaceOrientationsFor window: UIWindow?) -> UIInterfaceOrientationMask {
        return .portrait
    }

    func application(_ application: UIApplication, shouldSaveSecureApplicationState coder: NSCoder) -> Bool {
        StateKeeperUtilsKt.save(coder: coder, state: stateKeeper.save())
        return true
    }

    func application(_ application: UIApplication, shouldRestoreSecureApplicationState coder: NSCoder) -> Bool {
        //        stateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: StateKeeperUtilsKt.restore(coder: coder))
        return true
    }
}
