import SwiftUI
import UIKit
import PocketBucket

@main
struct PocketBucketApp: App {
    var body: some Scene {
        WindowGroup {
            PocketBucketView()
                .ignoresSafeArea(.keyboard)
        }
    }
}

private struct PocketBucketView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }
    func updateUIViewController(_ controller: UIViewController, context: Context) {}
}
