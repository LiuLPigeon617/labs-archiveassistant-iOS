package com.lyihub.archiveassistant.domain

import kotlinx.serialization.Serializable

enum class AiEngineType {
  OPENAI_COMPATIBLE,
  OPENAI_RESPONSES,
  ANTHROPIC,
  GEMINI,
  LOCAL_MODEL,
}

enum class LocalModelStatus {
  NOT_DOWNLOADED,
  DOWNLOADING,
  DOWNLOADED,
  INITIALIZING,
  READY,
  INFERENCING,
  ERROR,
  STOPPING,
}

enum class InferenceBackend {
  NPU,
  GPU,
  CPU,
  UNKNOWN,
}

@Serializable
data class LocalModelInfo(
  val id: String,
  val displayName: String,
  val fileName: String,
  val downloadUrl: String,
  val expectedSha256: String,
  val sizeBytes: Long,
)

@Serializable
data class LocalModelState(
  val status: LocalModelStatus = LocalModelStatus.NOT_DOWNLOADED,
  val downloadProgress: Float = 0f,
  val downloadBytes: Long = 0,
  val totalBytes: Long = 0,
  val activeBackend: InferenceBackend = InferenceBackend.UNKNOWN,
  val errorMessage: String? = null,
  val modelPath: String? = null,
)

@Serializable
data class BenchResult(
  val promptTokens: Int,
  val generateTokens: Int,
  val timeToFirstTokenMs: Long,
  val prefillTokensPerSecond: Float,
  val decodeTokensPerSecond: Float,
  val backend: InferenceBackend,
)

@Serializable
data class AiEngineSettings(
  val engineType: AiEngineType = AiEngineType.OPENAI_COMPATIBLE,
  val baseUrl: String = "https://api.example.com/v1",
  val modelName: String = "mock-knowledge-classifier",
  val apiKeyAlias: String = "default",
  val apiKey: String = "",
  val localModelId: String? = null,
  val localBackendPreference: InferenceBackend = InferenceBackend.NPU,
)

@Serializable
data class AiEnginePreset(
  val name: String,
  val engineType: AiEngineType = AiEngineType.OPENAI_COMPATIBLE,
  val baseUrl: String = "",
  val modelName: String = "",
  val apiKey: String = "",
)

/**
 * The Android build carried a `localEndpoint` field for an out-of-process HTTP inference server.
 * It is deliberately dropped here: the app now runs LiteRT-LM in-process, and iOS has no
 * equivalent local HTTP daemon. See `AiEngineSettings.localEndpoint` @Deprecated note in git history.
 */
@Serializable
data class ClassificationPayload(
  val topicId: String,
  val contentType: ContentType,
  val title: String,
  val summary: String,
  val rawInput: String,
  val documentFormat: DocumentFormat? = null,
)

sealed interface ClassificationResult {
  data class Classified(val payload: ClassificationPayload) : ClassificationResult

  data class BlankInput(val message: String = "请输入要归档的内容") : ClassificationResult

  data object Unknown : ClassificationResult
}
