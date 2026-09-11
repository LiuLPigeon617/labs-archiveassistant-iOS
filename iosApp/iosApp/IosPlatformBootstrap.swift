import Foundation
import SharedKit

/// Installs the Swift implementations that the Kotlin shared module calls for file I/O and bundle
/// access.
///
/// The Kotlin side deliberately avoids Kotlin/Native Foundation bindings; Swift owns every
/// byte-level operation using FileManager and Bundle, which are stable, well-documented APIs.
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

    // A Kotlin `object` is exposed to Swift as a class with a `shared` singleton accessor.
    let bridge = IosNativeBridge.shared

    bridge.itemsDir = { itemsDir }
    bridge.modelsDir = { modelsDir }

    bridge.exists = { path in
      fm.fileExists(atPath: path)
    }

    bridge.writeBytes = { path, data in
      let nsData = data.toNSData()
      try? nsData.write(to: URL(fileURLWithPath: path), options: .atomic)
    }

    bridge.readBytes = { path in
      guard let data = fm.contents(atPath: path) else { return nil }
      return data.toKotlinByteArray()
    }

    bridge.bundleResourcePath = { name, ext in
      Bundle.main.url(forResource: name, withExtension: ext.isEmpty ? nil : ext)?.path
    }
  }
}

// MARK: - Data <-> KotlinByteArray

private extension Data {
  func toKotlinByteArray() -> KotlinByteArray {
    let bytes = [UInt8](self)
    let result = KotlinByteArray(size: Int32(bytes.count))
    for (index, byte) in bytes.enumerated() {
      result.set(index: Int32(index), value: KotlinByte(bitPattern: byte))
    }
    return result
  }
}

private extension KotlinByteArray {
  func toNSData() -> Data {
    var bytes = [UInt8]()
    bytes.reserveCapacity(Int(size))
    for i in 0..<size {
      bytes.append(UInt8(bitPattern: get(index: i)))
    }
    return Data(bytes)
  }
}
