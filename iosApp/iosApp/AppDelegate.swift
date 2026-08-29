import UIKit

/// Minimal app delegate.
///
/// SwiftUI apps normally need no delegate, but one is retained so the Kotlin shared module has an
/// early, well-defined place to install platform hooks (for example assigning the Android
/// `AndroidPlatformContext` equivalent, or seeding a logger) before any view loads.
class AppDelegate: NSObject, UIApplicationDelegate {
  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    SharedStateBridgeKt.doInitKoinIos()
    return true
  }
}
