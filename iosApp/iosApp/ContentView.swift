import UIKit
import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    var shortcutAction: String?
    var deepLinkRoute: String?

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController(
            shortcutAction: shortcutAction,
            deepLinkRoute: deepLinkRoute
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var shortcutAction: String?
    var deepLinkRoute: String?

    var body: some View {
        ComposeView(shortcutAction: shortcutAction, deepLinkRoute: deepLinkRoute)
            .ignoresSafeArea(.all)
    }
}
