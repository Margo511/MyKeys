import UIKit
import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    let runtime: IosAuthRuntime

    func makeUIViewController(context: Context) -> UIViewController {
        runtime.makeViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    private let runtime: IosAuthRuntime

    init() {
        let url = Bundle.main.object(forInfoDictionaryKey: "MY_KEYS_SUPABASE_URL") as? String ?? ""
        let key = Bundle.main.object(forInfoDictionaryKey: "MY_KEYS_SUPABASE_PUBLISHABLE_KEY") as? String ?? ""
        runtime = IosAuthRuntime(supabaseUrl: url, publishableKey: key)
    }

    var body: some View {
        ComposeView(runtime: runtime)
            .ignoresSafeArea()
            .onOpenURL { url in
                runtime.handleDeepLink(uri: url.absoluteString)
            }
    }
}
