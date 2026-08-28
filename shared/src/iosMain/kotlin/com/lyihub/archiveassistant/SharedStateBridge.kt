package com.lyihub.archiveassistant

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.LocalModelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Swift-facing entry points.
 *
 * Kotlin coroutines cannot be consumed from Swift directly, and `StateFlow` is not observable by
 * SwiftUI. These helpers expose a plain-callback observer that Swift republishes into Combine.
 */
object SharedStateBridgeKt {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

  fun makeStateStore(): SharedStateStore = SharedStateStore()

  fun observeState(store: SharedStateStore, onState: (StateSnapshot) -> Unit) {
    store.snapshots.onEach { onState(it) }.launchIn(scope)
  }
}

/**
 * A Swift-visible snapshot of the settings screen.
 *
 * Only [String] and primitives cross the boundary: Kotlin enums become awkward Objective-C generics
 * in Swift, so they are flattened to their names here and re-parsed in Swift.
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
 * dependencies and lands in a later commit. Keeping the surface small now means the iOS shell can
 * build and run while the state layer is being de-Android-ified.
 */
class SharedStateStore {
  private var settings = AiEngineSettings()
  private var modelStatus: LocalModelStatus = LocalModelStatus.NOT_DOWNLOADED
  private var progress: Float = 0f

  val snapshots: kotlinx.coroutines.flow.Flow<StateSnapshot> =
    kotlinx.coroutines.flow.flowOf(currentSnapshot())

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

  fun updateAiSettings(
    engineType: String,
    baseUrl: String,
    modelName: String,
    apiKey: String,
  ) {
    settings =
      settings.copy(
        engineType = runCatching { AiEngineType.valueOf(engineType) }.getOrDefault(settings.engineType),
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
    modelStatus !in
      setOf(
        LocalModelStatus.INITIALIZING,
        LocalModelStatus.READY,
        LocalModelStatus.NOT_DOWNLOADED,
      )

  fun canStopModel(): Boolean =
    modelStatus in setOf(LocalModelStatus.READY, LocalModelStatus.INITIALIZING)

  fun startModel() {
    // Wired once the LiteRT-LM Swift adapter lands; see LocalLlmEngine expect/actual.
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
