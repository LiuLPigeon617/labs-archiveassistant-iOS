package com.lyihub.archiveassistant

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.LocalModelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

// Swift-facing entry points, declared as top-level functions on purpose.
//
// A Kotlin `object` is exported to Swift as a class with a `.shared` accessor, whereas top-level
// functions in SharedStateBridge.kt are exported as class methods on the `SharedStateBridgeKt`
// facade. Swift calls these as `SharedStateBridgeKt.makeStateStore()`, which is only valid for the
// latter form.

private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Called once from `AppDelegate.application(_:didFinishLaunchingWithOptions:)`.
 *
 * State observation starts here so the UIApplication run loop is already running. No DI container is
 * used (constructor defaults provide the seams), so this is currently just a defined init hook.
 */
fun doInitKoinIos() {
  println("[SharedStateBridge] iOS shared module initialized")
}

fun makeStateStore(): SharedStateStore = SharedStateStore()

/** Bridges a Kotlin `StateFlow` into a plain callback that Swift republishes through Combine. */
fun observeState(store: SharedStateStore, onState: (StateSnapshot) -> Unit) {
  store.snapshots.onEach { onState(it) }.launchIn(bridgeScope)
}

/**
 * A Swift-visible snapshot of the settings screen.
 *
 * Only [String] and primitives cross the boundary: Kotlin enums become awkward Objective-C generics
 * in Swift, so they are flattened to their names here and re-parsed on the Swift side.
 */
data class StateSnapshot(
  val engineType: String,
  val baseUrl: String,
  val modelName: String,
  val apiKey: String,
  val backendPreference: String,
  val localModelStatusText: String,
  val downloadProgress: Float,
  val isLocalModelDownloading: Boolean,
)

/**
 * Minimal state store exposed to iOS today.
 *
 * This is a placeholder boundary: the full `ArchiveAssistantStateStore` still carries Android
 * dependencies and moves in a later commit. Keeping the surface small means the iOS shell can build
 * and run while the state layer is being de-Android-ified.
 */
class SharedStateStore {
  private var settings = AiEngineSettings()
  private var modelStatus: LocalModelStatus = LocalModelStatus.NOT_DOWNLOADED
  private var progress: Float = 0f

  val snapshots: Flow<StateSnapshot> = flowOf(currentSnapshot())

  private fun currentSnapshot(): StateSnapshot =
    StateSnapshot(
      engineType = settings.engineType.name,
      baseUrl = settings.baseUrl,
      modelName = settings.modelName,
      apiKey = settings.apiKey,
      backendPreference = settings.localBackendPreference.name,
      localModelStatusText = modelStatus.describe(),
      downloadProgress = progress,
      isLocalModelDownloading = modelStatus == LocalModelStatus.DOWNLOADING,
    )

  fun updateAiSettings(engineType: String, baseUrl: String, modelName: String, apiKey: String) {
    settings =
      settings.copy(
        engineType =
          runCatching { AiEngineType.valueOf(engineType) }.getOrDefault(settings.engineType),
        baseUrl = baseUrl,
        modelName = modelName,
        apiKey = apiKey,
      )
  }

  fun updateBackendPreference(name: String) {
    val backend = runCatching { InferenceBackend.valueOf(name) }.getOrNull() ?: return
    settings = settings.copy(localBackendPreference = backend)
  }

  fun canStartModel(): Boolean =
    modelStatus in setOf(LocalModelStatus.DOWNLOADED, LocalModelStatus.ERROR)

  fun canStopModel(): Boolean =
    modelStatus in setOf(LocalModelStatus.READY, LocalModelStatus.INITIALIZING)

  fun startModel() {
    // Wired once the LiteRT-LM Swift adapter lands; see the LocalLlmEngine expect/actual.
  }

  fun stopModel() {
    // Wired once the LiteRT-LM Swift adapter lands.
  }

  private fun LocalModelStatus.describe(): String =
    when (this) {
      LocalModelStatus.NOT_DOWNLOADED -> "未下载"
      LocalModelStatus.DOWNLOADING -> "下载中"
      LocalModelStatus.DOWNLOADED -> "已下载"
      LocalModelStatus.INITIALIZING -> "初始化中"
      LocalModelStatus.READY -> "就绪"
      LocalModelStatus.INFERENCING -> "推理中"
      LocalModelStatus.ERROR -> "错误"
      LocalModelStatus.STOPPING -> "停止中"
    }
}
