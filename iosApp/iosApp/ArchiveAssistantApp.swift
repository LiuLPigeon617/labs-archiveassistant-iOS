import SwiftUI

/// App entry point.
///
/// The shell is native SwiftUI so iPad gets real `NavigationSplitView`, multi-window scenes,
/// Stage Manager and pointer/keyboard support. Settings are native SwiftUI too.
///
/// The panes are still SwiftUI stand-ins: `HomePane` and `DetailPane` remain in the Android `:app`
/// module. The migrated Compose tree is hosted by `ComposeRootPreviewView`, reachable from the
/// toolbar, rather than being mounted where a real pane belongs.
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
