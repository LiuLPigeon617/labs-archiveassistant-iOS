import SwiftUI

/// The product screen: the shared Compose tree, edge to edge.
///
/// Before this, the app opened on a `NavigationSplitView` whose two columns were both
/// `UnmigratedPanePlaceholder`s, and the real Compose UI was reachable only by tapping a toolbar
/// button that presented it as a `fullScreenCover`. That arrangement existed because no pane had been
/// migrated: a split view with nothing to split was still better than a blank screen.
///
/// Now that `:shared` owns the dashboard, the settings screen and the briefing screen, the shell has
/// nothing of its own to draw. It therefore hosts
/// `ArchiveComposeHostingViewController` directly and gets out of the way:
///
/// - **No `NavigationStack` and no toolbar.** `HomePane` draws its own imperial header and its own
///   settings entry (the Composable gear calls into the shared store, which then renders
///   `SettingsPane`). A native navigation bar on top of that duplicated the title and put a second,
///   differently-backed settings screen one tap away — the native one binds to `SharedStateBridge`;
///   a placeholder store that silently discards every edit.
/// - **`.ignoresSafeArea()`.** `ComposeUIViewController` applies the safe-area insets to the
///   composition itself, so the hosting view must span the full window; clipping it to the safe area
///   as well would subtract the inset twice.
/// - **Explicit constraints still matter.** See `ArchiveComposeHostingViewController`: Compose reports
///   no usable intrinsic size, so the representable is pinned by the hosting controller rather than
///   self-sizing.
///
/// Nothing reads launch arguments here any more. `--compose-preview` used to present the Compose tree
/// instead of the placeholder shell; with Compose behind no switch, the argument has no reader and was
/// removed from `.github/scripts/simulator-smoke.sh` rather than left as a passing decoration.
struct MainWorkspaceView: View {
  var body: some View {
    ArchiveComposeHostingViewController.Representable()
      .ignoresSafeArea()
  }
}

#Preview {
  MainWorkspaceView()
}
