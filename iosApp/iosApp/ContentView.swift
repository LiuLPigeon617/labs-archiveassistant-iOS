import SwiftUI

/// Placeholder home view.
///
/// The shipped home and detail panes are rendered by Compose Multiplatform inside
/// `ComposeHostingViewController`. This view exists so the project has a valid root view while the
/// Compose screens are being migrated; it is not part of the product UI.
struct ContentView: View {
  var body: some View {
    VStack(spacing: 16) {
      Image(systemName: "archivebox")
        .font(.system(size: 48))
        .foregroundStyle(.secondary)
      Text("聚合拾遗")
        .font(.title)
      Text("Compose 内容面板迁移中")
        .font(.subheadline)
        .foregroundStyle(.secondary)
    }
    .padding()
  }
}

#Preview {
  ContentView()
}
