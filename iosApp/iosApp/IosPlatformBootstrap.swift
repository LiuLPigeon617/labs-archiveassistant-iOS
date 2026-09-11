import Foundation
import SharedKit

/// Installs the Swift implementations that the Kotlin shared module calls for file I/O and bundle
/// access.
///
/// The Kotlin side deliberately avoids Kotlin/Native Foundation bindings; Swift owns every
/// byte-level operation using FileManager and Bundle, which are stable, well-documented APIs.
///
/// Payloads cross the boundary as Base64 strings: Swift's `KotlinByteArray` interop needs
/// per-element accessors and is easy to get subtly wrong, whereas Base64 is a plain `String` on
/// both sides.
enum IosPlatformBootstrap {
  static func install() {
    let fm = FileManager.default

    let appSupport: String = {
      let url =
        fm.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
        ?? fm.temporaryDirectory
      // The directory must exist before Kotlin writes into it.
      try? fm.createDirectory(at: url, withIntermediateDirectories: true)
      return url.path
    }()

    let itemsDir = (appSupport as NSString).appendingPathComponent("items")
    let modelsDir = (appSupport as NSString).appendingPathComponent("models")
    try? fm.createDirectory(atPath: itemsDir, withIntermediateDirectories: true)
    try? fm.createDirectory(atPath: modelsDir, withIntermediateDirectories: true)

    // A Kotlin `object` is exported to Swift as a class with a `shared` singleton accessor.
    let bridge = IosNativeBridge.shared

    bridge.itemsDir = { itemsDir }
    bridge.modelsDir = { modelsDir }

    bridge.exists = { path in
      fm.fileExists(atPath: path)
    }

    bridge.writeBase64 = { path, base64 in
      guard let data = Data(base64Encoded: base64) else { return }
      try? data.write(to: URL(fileURLWithPath: path), options: .atomic)
    }

    bridge.readBase64 = { path in
      guard let data = fm.contents(atPath: path) else { return nil }
      return data.base64EncodedString()
    }

    // Bundled mock assets are not shipped yet; returning nil makes materialize fall back to the
    // item's existing source instead of failing.
    bridge.bundleResourcePath = { name, ext in
      Bundle.main.url(forResource: name, withExtension: ext.isEmpty ? nil : ext)?.path
    }
  }
}
