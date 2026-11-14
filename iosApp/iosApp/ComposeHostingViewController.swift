import SharedKit
import SwiftUI
import UIKit

/// Bridges the shared Compose root into SwiftUI.
///
/// This used to render a SwiftUI stand-in, because `SharedKit` exposed no
/// `ComposeUIViewController` factory and the Compose UI had not been migrated into the shared
/// module. `:shared` now ships `IosComposeRoot.makeViewController()`, so the real Compose tree is
/// hosted here.
///
/// Sizing note that will keep mattering as panes land: `ComposeUIViewController` does not report a
/// reliable intrinsic content size, so the view is always pinned with explicit constraints. Relying
/// on self-sizing collapses it to zero height.
///
/// Container note: the controller below is only a container. `ComposeUIViewController` states its
/// own supported orientations, and this module's `Info.plist` allows iPad multitasking, so a
/// container that inherited the default (portrait-only) rules would silently break landscape. The
/// overrides forward to the child instead of hard-coding a set.
final class ArchiveComposeHostingViewController: UIViewController {
  private let compose: UIViewController

  init(compose: UIViewController) {
    self.compose = compose
    super.init(nibName: nil, bundle: nil)
  }

  @available(*, unavailable)
  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func viewDidLoad() {
    super.viewDidLoad()

    addChild(compose)
    view.addSubview(compose.view)
    compose.view.translatesAutoresizingMaskIntoConstraints = false
    NSLayoutConstraint.activate([
      compose.view.topAnchor.constraint(equalTo: view.topAnchor),
      compose.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
      compose.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
      compose.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
    ])
    compose.didMove(toParent: self)
  }

  override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
    compose.supportedInterfaceOrientations
  }

  override var shouldAutorotate: Bool {
    compose.shouldAutorotate
  }
}

extension ArchiveComposeHostingViewController {
  struct Representable: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> ArchiveComposeHostingViewController {
      ArchiveComposeHostingViewController(compose: IosComposeRoot.shared.makeViewController())
    }

    func updateUIViewController(
      _ uiViewController: ArchiveComposeHostingViewController,
      context: Context
    ) {
      // State flows through the shared Kotlin store, so no imperative update is needed here.
    }
  }
}
