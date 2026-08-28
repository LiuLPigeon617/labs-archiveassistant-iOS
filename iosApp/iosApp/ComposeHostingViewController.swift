import SwiftUI
import SharedKit
import UIKit

/// Bridges a Compose Multiplatform screen into SwiftUI.
///
/// `ComposeUIViewController` does not provide a reliable intrinsic content size to SwiftUI, so the
/// Compose view must be given an explicit, non-zero frame. Relying on self-sizing leads to a
/// zero-height (collapsed) layout. See `MainWorkspaceView` for how a container supplies the size.
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
    let composeController = MainViewControllerKt.makeComposeController(screen: screen)
    addChild(composeController)
    view.addSubview(composeController.view)
    composeController.view.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      composeController.view.topAnchor.constraint(equalTo: view.topAnchor),
      composeController.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      composeController.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      composeController.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
    ])
    composeController.didMove(toParent: self)
  }
}

extension ComposeHostingViewController {
  struct Representable: UIViewControllerRepresentable {
    let screen: Screen

    func makeUIViewController(context: Context) -> ComposeHostingViewController {
      ComposeHostingViewController(screen: screen)
    }

    func updateUIViewController(_ uiViewController: ComposeHostingViewController, context: Context) {
      // State flows through the shared Kotlin store, so no imperative update is needed here.
    }

    /// Provide a concrete sizing proposal; without it SwiftUI may size the Compose subtree to 0.
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
