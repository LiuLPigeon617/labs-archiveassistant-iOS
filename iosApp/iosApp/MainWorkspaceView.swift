import SwiftUI

/// iPad-first shell: a two-column split view with the Compose-rendered home pane on the left and
/// detail on the right. On iPhone this collapses to a single navigation stack automatically.
struct MainWorkspaceView: View {
  @EnvironmentObject private var state: SharedStateBridge
  @State private var showingSettings = false
  @State private var columnVisibility: NavigationSplitViewVisibility = .doubleColumn

  var body: some View {
    NavigationSplitView(columnVisibility: $columnVisibility) {
      // Leading column: Compose-rendered topic list + parser input.
      ComposeHostingViewController.Representable(screen: .home)
        .ignoresSafeArea(edges: .top)
        .navigationTitle("聚合拾遗")
        .toolbar {
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
      // Trailing column: Compose-rendered detail / memorial panes.
      ComposeHostingViewController.Representable(screen: .detail)
        .ignoresSafeArea(edges: .top)
    }
    .navigationSplitViewStyle(.balanced)
    .sheet(isPresented: $showingSettings) {
      SettingsView()
        .environmentObject(state)
    }
  }
}
