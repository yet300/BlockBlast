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
            ComposeView(root: appDelegate.root, backDispatcher: appDelegate.backDispatcher)
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

            let rootVC = UIApplication.shared.connectedScenes
                .compactMap { $0 as? UIWindowScene }
                .flatMap { $0.windows }
                .first(where: { $0.isKeyWindow })?
                .rootViewController

            ConsentManager.shared.gather(from: rootVC) {
                // Ads may now be requested — preload the game-over interstitial.
                Task { @MainActor in
                    await AdCoordinator.shared.loadInterstitial()
                }
            }
        }
        return true
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

    func application(_ application: UIApplication, shouldSaveSecureApplicationState coder: NSCoder) -> Bool {
        StateKeeperUtilsKt.save(coder: coder, state: stateKeeper.save())
        return true
    }

    func application(_ application: UIApplication, shouldRestoreSecureApplicationState coder: NSCoder) -> Bool {
        //        stateKeeper = StateKeeperDispatcherKt.StateKeeperDispatcher(savedState: StateKeeperUtilsKt.restore(coder: coder))
        return true
    }
}
