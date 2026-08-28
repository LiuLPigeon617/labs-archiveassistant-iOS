import SwiftUI

/// App entry point.
///
/// The shell is native SwiftUI so iPad gets real `NavigationSplitView`, multi-window scenes,
/// Stage Manager and pointer/keyboard support. Content panes are rendered by Compose
/// Multiplatform inside `ComposeHostingViewController`; settings are native SwiftUI.
@main
struct ArchiveAssistantApp: App {
  @StateObject private var state = SharedStateBridge.shared

  var body: some Scene {
    WindowGroup {
      MainWorkspaceView()
        .environmentObject(state)
    }
    // iPad: expose multiple windows so the user can open two topics side by side.
    .windowResizability(.contentSize)
  }
}
