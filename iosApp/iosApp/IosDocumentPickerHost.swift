import Foundation
import SharedKit
import UIKit
import UniformTypeIdentifiers

/// Presents `UIDocumentPickerViewController` for the shared module and hands back a readable path.
///
/// Kotlin cannot present a view controller, so `IosNativeBridge.pickFile` routes here. Two details
/// matter and are the reason this is not a one-liner:
///
/// 1. A document from the Files app is security-scoped. The scope must be opened before its bytes can
///    be read and closed again afterwards; a URL that outlives its scope reads as an empty file.
/// 2. The shared module reads through `FileManager.contents(atPath:)`, which only works for a plain
///    file path. When a provider ignores the `asCopy` flag the picked file is copied into the app's
///    temporary directory first, and the copy's path is what crosses the bridge.
///
/// The delegate is retained here: `UIDocumentPickerViewController.delegate` is weak, so a local
/// delegate would be deallocated before the user finishes choosing.
final class IosDocumentPickerHost: NSObject, UIDocumentPickerDelegate {
  static let shared = IosDocumentPickerHost()

  private var pendingCallback: ((String?) -> Void)?

  func present(allowedExtensions: [String], onPicked: @escaping (String?) -> Void) {
    // Two picks at once would share one continuation; the second request is reported as cancelled.
    guard pendingCallback == nil else {
      onPicked(nil)
      return
    }
    guard let presenter = Self.topViewController() else {
      onPicked(nil)
      return
    }

    let types =
      allowedExtensions.isEmpty
      ? [UTType.item]
      : allowedExtensions.compactMap { UTType(filenameExtension: $0) }

    let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
    picker.delegate = self
    picker.allowsMultipleSelection = false
    pendingCallback = onPicked
    presenter.present(picker, animated: true)
  }

  func documentPicker(
    _ controller: UIDocumentPickerViewController,
    didPickDocumentsAt urls: [URL]
  ) {
    guard let callback = takeCallback() else { return }
    guard let url = urls.first else {
      callback(nil)
      return
    }

    let scoped = url.startAccessingSecurityScopedResource()
    defer {
      if scoped { url.stopAccessingSecurityScopedResource() }
    }

    var destination = url
    if scoped {
      let temporary = FileManager.default.temporaryDirectory
        .appendingPathComponent("picked-\(UUID().uuidString)-\(url.lastPathComponent)")
      do {
        try FileManager.default.copyItem(at: url, to: temporary)
        destination = temporary
      } catch {
        callback(nil)
        return
      }
    }

    callback(destination.path)
  }

  func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
    takeCallback()?(nil)
  }

  private func takeCallback() -> ((String?) -> Void)? {
    defer { pendingCallback = nil }
    return pendingCallback
  }

  private static func topViewController() -> UIViewController? {
    let scene = UIApplication.shared.connectedScenes
      .compactMap { $0 as? UIWindowScene }
      .first { $0.activationState == .foregroundActive }

    var root = scene?.windows.first { $0.isKeyWindow }?.rootViewController
    while let presented = root?.presentedViewController {
      root = presented
    }
    return root
  }
}
