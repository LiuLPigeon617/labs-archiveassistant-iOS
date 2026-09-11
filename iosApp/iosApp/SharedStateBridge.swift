import Combine
import Foundation
import SharedKit

/// `ObservableObject` façade over the Kotlin shared state store.
///
/// Kotlin `StateFlow` cannot be observed directly by SwiftUI, so this class subscribes through
/// `IosAppBridge` and republishes into Combine. SwiftUI views bind to the `@Published` properties.
///
/// Kotlin/Native mapping note: a `Boolean` returned from a class member surfaces as Swift `Bool`,
/// whereas a `Boolean` inside a *function type* (such as the closure in `IosNativeBridge`) is boxed
/// as `KotlinBoolean`. Hence no `.boolValue` here, but `KotlinBoolean(bool:)` there.
final class SharedStateBridge: ObservableObject {
  static let shared = SharedStateBridge()

  private let store: SharedStateStore

  // AI engine settings
  @Published var engineType: String = "OPENAI_COMPATIBLE"
  @Published var baseUrl: String = ""
  @Published var modelName: String = ""
  @Published var apiKey: String = ""

  // Local model
  @Published var backendPreference: String = "NPU"
  @Published var localModelStatusText: String = "未下载"
  @Published var downloadProgress: Double = 0
  @Published var isLocalModelDownloading = false

  private init() {
    store = IosAppBridge.shared.makeStateStore()
    observeState()
  }

  private func observeState() {
    IosAppBridge.shared.observeState(store: store) { [weak self] snapshot in
      guard let self else { return }
      DispatchQueue.main.async {
        self.engineType = snapshot.engineType
        self.baseUrl = snapshot.baseUrl
        self.modelName = snapshot.modelName
        self.apiKey = snapshot.apiKey
        self.backendPreference = snapshot.backendPreference
        self.localModelStatusText = snapshot.localModelStatusText
        self.downloadProgress = Double(snapshot.downloadProgress)
        self.isLocalModelDownloading = snapshot.isLocalModelDownloading
      }
    }
  }

  var appVersion: String {
    Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
  }

  var canStartModel: Bool { store.canStartModel() }

  var canStopModel: Bool { store.canStopModel() }

  func startModel() { store.startModel() }

  func stopModel() { store.stopModel() }

  func updateAiSettings() {
    store.updateAiSettings(
      engineType: engineType,
      baseUrl: baseUrl,
      modelName: modelName,
      apiKey: apiKey
    )
  }
}
