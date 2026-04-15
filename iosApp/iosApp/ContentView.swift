import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let root: RootComponent
    let backDispatcher: BackDispatcher

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(root: root, backDispatcher: backDispatcher)

        if #available(iOS 15.0, *) {
            controller.view.minimumContentSizeCategory = .large
            controller.view.maximumContentSizeCategory = .large
        }

        return controller
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}


