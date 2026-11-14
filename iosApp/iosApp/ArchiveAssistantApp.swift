import SwiftUI

/// App entry point.
///
/// The shell is deliberately thin: `MainWorkspaceView` mounts the shared Compose root, and the
/// Compose side draws everything — the imperial header, the dashboard, the briefing screen and the
/// settings pane. Nothing in SwiftUI sits above it any more.
///
/// This used to be different. The shell was a native `NavigationSplitView` with two placeholder
/// columns, a native `SettingsView` behind a toolbar gear, and a `SharedStateBridge` that published
/// the Kotlin store into SwiftUI through `@EnvironmentObject`. Both of those predate the migration:
/// `SharedStateBridge` binds to `SharedStateStore`, a placeholder whose `updateAiSettings()` only
/// mutated a field and whose `startModel()`/`stopModel()` had empty bodies. A screen built on it
/// accepted edits and silently dropped them, which is worse than having no screen at all — so the
/// `@StateObject` is gone and `SettingsPane` (which reaches the real `ArchiveAssistantStateStore`)
/// is the only settings surface left.
///
/// `SharedStateBridge.swift`, `SettingsView.swift` and `ContentView.swift` are still in the target
/// but nothing instantiates them; each carries a warning at the top explaining what it was. Deleting
/// them needs matching `project.pbxproj` surgery that cannot be compiled on this machine, so it is a
/// separate decision rather than a side effect of this one.
@main
struct ArchiveAssistantApp: App {
  /// Required for `AppDelegate` to run. Without this adaptor SwiftUI never invokes the delegate, so
  /// the Kotlin shared module would never receive its file and bundle implementations.
  @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

  var body: some Scene {
    WindowGroup {
      MainWorkspaceView()
    }
  }
}
