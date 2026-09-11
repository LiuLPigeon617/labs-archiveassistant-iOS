import Combine
import Foundation
import SharedKit

/// `ObservableObject` façade over the Kotlin shared state store.
///
/// Kotlin `StateFlow` cannot be observed directly by SwiftUI, so this class subscribes via the
/// `CoroutineScope`-backed collector exposed by `SharedStateBridgeKt` and republishes into Combine.
/// SwiftUI views bind to the `@Published` properties below.
final class SharedStateBridge: ObservableObject {
  static let shared = SharedStateBridge()

  private let store: SharedStateStore
  private var cancellables = Set<AnyCancellable>()

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
    store = SharedStateBridgeKt.makeStateStore()
    observeState()
  }

  private func observeState() {
    SharedStateBridgeKt.observeState(store: store) { [weak self] snapshot in
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
