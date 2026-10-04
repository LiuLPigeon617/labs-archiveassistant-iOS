import SwiftUI

/// Standalone host for the shared Compose root.
///
/// This is a **staging surface, not a product screen.** The shared module renders one pane today
/// (`SettingsPane`); `HomePane` and `DetailPane` are still in the Android `:app` module. So instead
/// of mounting Compose into one of the real panes — which would put a settings screen where the
/// entry detail belongs — the migrated Compose tree gets its own entry point that is reachable and
/// clearly separate, and that can be deleted in one piece once the remaining panes land.
///
/// Its reason to exist is verification: it is the only place the imperial fonts and the Compose
/// resource pipeline actually render on iOS. Before this, `ProvideImperialFonts` and
/// `archiveFontFamily` had no caller, so those bundled fonts never reached a composable anywhere.
struct ComposeRootPreviewView: View {
  @Environment(\.dismiss) private var dismiss
  @EnvironmentObject private var state: SharedStateBridge

  var body: some View {
    NavigationStack {
      ArchiveComposeHostingViewController.Representable()
        .ignoresSafeArea(edges: .bottom)
        .navigationTitle("共享 Compose 预览")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
          ToolbarItem(placement: .navigationBarTrailing) {
            Button("关闭") {
              dismiss()
            }
          }
        }
    }
  }
}

#Preview {
  ComposeRootPreviewView()
    .environmentObject(SharedStateBridge.shared)
}
