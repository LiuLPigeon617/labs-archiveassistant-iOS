import SwiftUI
import UIKit

/// Bridges a Compose Multiplatform screen into SwiftUI.
///
/// IMPORTANT — this is a placeholder boundary, not the finished bridge.
///
/// The Compose UI layer (home, detail, memorial reader) has not been migrated into `:shared` yet, so
/// `SharedKit` does not expose a `ComposeUIViewController` factory. Until it does, this controller
/// renders a SwiftUI stand-in so the app shell builds and runs, which is what the CI pipeline
/// verifies.
///
/// When the Compose screens land in `:shared`, replace the body of `viewDidLoad` with:
///
///     let controller = MainViewControllerKt.makeComposeController(screen: screen)
///     addChild(controller)
///     view.addSubview(controller.view)
///     controller.view.translatesAutoresizingMaskIntoConstraints = false
///     NSLayoutConstraint.activate([... pin to all four edges ...])
///     controller.didMove(toParent: self)
///
/// Note the sizing constraint that applies once Compose is hosted here:
/// `ComposeUIViewController` does not provide a reliable intrinsic content size to SwiftUI, so the
/// Compose view must always be pinned with explicit constraints or given a concrete frame.
/// Relying on self-sizing causes the view to collapse to zero height.
final class ComposeHostingViewController: UIViewController {
  enum Screen {
    case home
    case detail
  }

  private let screen: Screen

  init(screen: Screen) {
    self.screen = screen
    super.init(nibName: nil, bundle: nil)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func viewDidLoad() {
    super.viewDidLoad()

    let host = UIHostingController(rootView: ComposePlaceholderView(screen: screen))
    addChild(host)
    view.addSubview(host.view)
    host.view.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      host.view.topAnchor.constraint(equalTo: view.topAnchor),
      host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
    ])
    host.didMove(toParent: self)
  }
}

extension ComposeHostingViewController {
  struct Representable: UIViewControllerRepresentable {
    let screen: Screen

    func makeUIViewController(context: Context) -> ComposeHostingViewController {
      ComposeHostingViewController(screen: screen)
    }

    func updateUIViewController(
      _ uiViewController: ComposeHostingViewController,
      context: Context
    ) {
      // State flows through the shared Kotlin store, so no imperative update is needed here.
    }

    /// Returns a concrete size because the hosted view has no usable intrinsic content size.
    func sizeThatFits(
      _ proposal: ProposedViewSize,
      uiViewController: ComposeHostingViewController,
      context: Context
    ) -> CGSize? {
      CGSize(
        width: proposal.width ?? UIScreen.main.bounds.width,
        height: proposal.height ?? UIScreen.main.bounds.height
      )
    }
  }
}

/// Stand-in for the Compose panes that are still to be migrated into `:shared`.
private struct ComposePlaceholderView: View {
  let screen: ComposeHostingViewController.Screen

  var body: some View {
    VStack(spacing: 12) {
      Image(systemName: screen == .home ? "archivebox" : "doc.text.magnifyingglass")
        .font(.system(size: 44))
        .foregroundStyle(.secondary)

      Text(screen == .home ? "聚合拾遗" : "条目详情")
        .font(.title2)

      Text("Compose 面板迁移中")
        .font(.footnote)
        .foregroundStyle(.secondary)
    }
    .frame(maxWidth: .infinity, maxHeight: .infinity)
    .background(Color(uiColor: .systemGroupedBackground))
  }
}
