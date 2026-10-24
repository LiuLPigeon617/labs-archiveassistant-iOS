import SharedKit
import UIKit

/// Minimal app delegate.
///
/// SwiftUI apps normally need no delegate, but one is retained so the Kotlin shared module has an
/// early, well-defined place to install platform hooks before any view loads: Swift must provide the
/// file and bundle implementations that the shared kernel calls, and that has to happen before the
/// first state store is constructed.
///
/// Wired up through `@UIApplicationDelegateAdaptor` in `ArchiveAssistantApp`.
class AppDelegate: NSObject, UIApplicationDelegate {
  func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
  ) -> Bool {
    IosPlatformBootstrap.install()
    IosAppBridge.shared.doInit()
    return true
  }

  /// Narrows the app's orientation mask when the smoke test asks for landscape.
  ///
  /// This is the only lever that works for the iPad pass: `simctl` has no rotation command, and on
  /// the CI runner `devicectl` had no usable `device orientation` subcommand (see the job log for run
  /// 39979857765, which still captured a 2064x2752 portrait iPad). The Info.plist already allows every
  /// orientation on iPad, so the default is SwiftUI's own behaviour and this only ever narrows it.
  ///
  /// Returning `.landscape` makes UIKit lay the window out wide at launch. The smoke test then asserts
  /// the screenshot's pixel width exceeds its height, so a silent no-op fails instead of passing.
  func application(
    _ application: UIApplication,
    supportedInterfaceOrientationsFor window: UIWindow?
  ) -> UIInterfaceOrientationMask {
    LaunchOptions.forcesLandscape ? .landscape : .all
  }
}
