import UIKit
import SwiftUI
import ComposeApp

struct ComposeView: UIViewControllerRepresentable {
    let root: RootComponent
    let backDispatcher: BackDispatcher
    let onFirstVisible: (UIViewController) -> Void

    func makeUIViewController(context: Context) -> UIViewController {
        let controller = MainViewControllerKt.MainViewController(root: root, backDispatcher: backDispatcher)

        if #available(iOS 15.0, *) {
            controller.view.minimumContentSizeCategory = .large
            controller.view.maximumContentSizeCategory = .large
        }

        return VisibleComposeViewController(contentController: controller, onFirstVisible: onFirstVisible)
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

private final class VisibleComposeViewController: UIViewController {
    private let contentController: UIViewController
    private let onFirstVisible: (UIViewController) -> Void
    private var didNotifyVisible = false

    init(contentController: UIViewController, onFirstVisible: @escaping (UIViewController) -> Void) {
        self.contentController = contentController
        self.onFirstVisible = onFirstVisible
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        addChild(contentController)
        view.addSubview(contentController.view)
        contentController.view.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            contentController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            contentController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            contentController.view.topAnchor.constraint(equalTo: view.topAnchor),
            contentController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
        contentController.didMove(toParent: self)
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        notifyVisibleIfNeeded()
    }

    private func notifyVisibleIfNeeded() {
        guard !didNotifyVisible else { return }
        didNotifyVisible = true
        onFirstVisible(self)
    }
}
