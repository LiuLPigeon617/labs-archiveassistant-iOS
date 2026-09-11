import UIKit

/// Minimal app delegate.
///
/// SwiftUI apps normally need no delegate, but one is retained so the Kotlin shared module has an
/// early, well-defined place to install platform hooks before any view loads: Swift must provide
/// the file and bundle implementations that the shared kernel calls, and that has to happen before
/// the first state store is constructed.
class AppDelegate: NSObject, UIApplicationDelegate {
  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    IosPlatformBootstrap.install()
    SharedStateBridgeKt.doInitKoinIos()
    return true
  }
}
