import SwiftUI

/// App entry point.
///
/// The shell is native SwiftUI so iPad gets real `NavigationSplitView`, multi-window scenes,
/// Stage Manager and pointer/keyboard support. Content panes are rendered by Compose Multiplatform
/// inside `ComposeHostingViewController`; settings are native SwiftUI.
@main
struct ArchiveAssistantApp: App {
  /// Required for `AppDelegate` to run. Without this adaptor SwiftUI never invokes the delegate, so
  /// the Kotlin shared module would never receive its file and bundle implementations.
  @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

  @StateObject private var state = SharedStateBridge.shared

  var body: some Scene {
    WindowGroup {
      MainWorkspaceView()
        .environmentObject(state)
    }
  }
}
