import ComposeApp
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
                    appDelegate.configureAdsPrivacyFlow(from: presenter)
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
        FirebaseApp.configure()
        return true
    }

    @MainActor
    func configureAdsPrivacyFlow(from presenter: UIViewController) {
        IosTrackingAuthorizationBridge.shared.requestAuthorization = { [weak presenter] in
            guard let presenter else { return }
            TrackingAuthorizationManager.shared.requestIfNeeded(from: presenter) {
                IosTrackingAuthorizationBridge.shared.markCompleted()
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
