import SwiftUI

/// Launch-time switches used by the CI simulator smoke test.
///
/// These exist because `xcrun simctl launch` can pass arguments but cannot tap a toolbar button, so a
/// screenshot of the migrated Compose tree would otherwise need a UI-automation target. Acting on
/// these flags is the whole of their effect — they do not change what the app renders, only where it
/// starts.
enum LaunchOptions {
  /// Open the shared Compose tree immediately instead of the two-column shell.
  ///
  /// Used by `.github/scripts/simulator-smoke.sh` to capture the migrated settings pane, and usable
  /// by hand: `xcrun simctl launch booted com.lyihub.archiveassistant --compose-preview`.
  static let opensComposePreview = ProcessInfo.processInfo.arguments.contains("--compose-preview")
}

/// iPad-first shell: a two-column split view. On iPhone this collapses to a single navigation stack
/// automatically.
///
/// Both columns are still placeholders — `HomePane` and `DetailPane` have not been migrated into
/// `:shared` — so neither one hosts Compose yet. The migrated Compose tree is reachable through the
/// toolbar's preview entry point instead; see `ComposeRootPreviewView` for why it is deliberately
/// kept out of the panes.
struct MainWorkspaceView: View {
  @EnvironmentObject private var state: SharedStateBridge
  @State private var showingSettings = false
  @State private var showingComposePreview = LaunchOptions.opensComposePreview
  @State private var columnVisibility: NavigationSplitViewVisibility = .doubleColumn

  var body: some View {
    NavigationSplitView(columnVisibility: $columnVisibility) {
      // Leading column: SwiftUI stand-in for the Compose-rendered topic list + parser input.
      UnmigratedPanePlaceholder(title: "聚合拾遗", systemImage: "archivebox")
        .ignoresSafeArea(edges: .top)
        .navigationTitle("聚合拾遗")
        .toolbar {
          ToolbarItem(placement: .navigationBarTrailing) {
            Button {
              showingComposePreview = true
            } label: {
              Image(systemName: "square.on.square.dashed")
                .accessibilityLabel("共享 Compose 预览")
            }
          }
          ToolbarItem(placement: .navigationBarTrailing) {
            Button {
              showingSettings = true
            } label: {
              // Native SF Symbol: the Android build had exactly one gear icon here, so replacing
              // it with the system icon costs no brand identity.
              Image(systemName: "gearshape")
                .accessibilityLabel("设置")
            }
          }
        }
    } detail: {
      // Trailing column: SwiftUI stand-in for the Compose-rendered detail / memorial panes.
      UnmigratedPanePlaceholder(title: "条目详情", systemImage: "doc.text.magnifyingglass")
        .ignoresSafeArea(edges: .top)
    }
    .navigationSplitViewStyle(.balanced)
    .sheet(isPresented: $showingSettings) {
      SettingsView()
        .environmentObject(state)
    }
    .fullScreenCover(isPresented: $showingComposePreview) {
      ComposeRootPreviewView()
        .environmentObject(state)
    }
  }
}

/// Temporary stand-in for a pane that lives in the Android `:app` module and has not been migrated
/// into `:shared` yet. Delete each use as its real Compose pane lands.
private struct UnmigratedPanePlaceholder: View {
  let title: String
  let systemImage: String

  var body: some View {
    VStack(spacing: 12) {
      Image(systemName: systemImage)
        .font(.system(size: 44))
        .foregroundStyle(.secondary)

      Text(title)
        .font(.title2)

      Text("Compose 面板迁移中")
        .font(.footnote)
        .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color(uiColor: .systemGroupedBackground))
  }
}

