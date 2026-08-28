package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.encodeToString

/**
 * Transport abstraction for remote AI calls.
 *
 * Preserved from the Android build so the 15 existing unit tests that inject a fake transport keep
 * working; only the real implementation changes (HttpURLConnection -> Ktor).
 */
interface RemoteAiTransport {
  suspend fun send(request: RemoteAiRequest): RemoteAiResponse
}

data class RemoteAiRequest(
  val endpoint: String,
  val method: String,
  val headers: Map<String, String>,
  val body: String?,
)

data class RemoteAiResponse(val code: Int, val body: String)

internal fun buildRemoteRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest =
  when (settings.engineType) {
    AiEngineType.OPENAI_COMPATIBLE -> openAiCompatibleSummaryRequest(settings, prompt)
    AiEngineType.OPENAI_RESPONSES -> openAiResponsesSummaryRequest(settings, prompt)
    AiEngineType.ANTHROPIC -> anthropicSummaryRequest(settings, prompt)
    AiEngineType.GEMINI -> geminiSummaryRequest(settings, prompt)
    AiEngineType.LOCAL_MODEL ->
      error("Local model summarization does not use RemoteApiSmartSummarizer")
  }

private fun openAiCompatibleSummaryRequest(
  settings: AiEngineSettings,
  prompt: String,
): RemoteAiRequest {
  val body = Json.encodeToString(ChatCompletionRequest(model = settings.modelName, messages = listOf(ChatMessage(content = prompt))))
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "chat/completions"),
    "POST",
    authHeaders(settings.apiKey),
    body,
  )
}

private fun openAiResponsesSummaryRequest(
  settings: AiEngineSettings,
  prompt: String,
): RemoteAiRequest {
  val body = Json.encodeToString(ResponsesRequest(model = settings.modelName, input = prompt))
  return RemoteAiRequest(apiEndpoint(settings.baseUrl, "responses"), "POST", authHeaders(settings.apiKey), body)
}

private fun anthropicSummaryRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest {
  val body =
    Json.encodeToString(
      AnthropicRequest(model = settings.modelName, messages = listOf(ChatMessage(content = prompt)))
    )
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "messages"),
    "POST",
    anthropicHeaders(settings.apiKey),
    body,
  )
}

private fun geminiSummaryRequest(settings: AiEngineSettings, prompt: String): RemoteAiRequest {
  val modelPath =
    if (settings.modelName.startsWith("models/")) settings.modelName
    else "models/${settings.modelName}"
  val body = Json.encodeToString(GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))))
  return RemoteAiRequest(
    apiEndpoint(settings.baseUrl, "$modelPath:generateContent") + "?key=${settings.apiKey.trim()}",
    "POST",
    emptyMap(),
    body,
  )
}

/**
 * Pulls the model's text out of a provider response.
 *
 * Each provider nests the text differently, so this walks the known shapes. [JsonElement] accessors
 * are used instead of fully-typed models on purpose: the app must tolerate extra fields and shape
 * drift across providers without failing the whole request.
 */
internal fun extractModelText(engineType: AiEngineType, responseBody: String): String {
  val json = Json.parseToJsonElement(responseBody).jsonObject
  return when (engineType) {
    AiEngineType.OPENAI_COMPATIBLE ->
      json
        .jsonArrayAt("choices")[0]
        .jsonObject
        .jsonObjectAt("message")
        .stringAt("content")

    AiEngineType.OPENAI_RESPONSES ->
      json.stringOrNull("output_text")?.takeIf { it.isNotBlank() }
        ?: json
          .jsonArrayAt("output")[0]
          .jsonObject
          .jsonArrayAt("content")[0]
          .jsonObject
          .stringAt("text")

    AiEngineType.ANTHROPIC -> json.jsonArrayAt("content")[0].jsonObject.stringAt("text")

    AiEngineType.GEMINI ->
      json
        .jsonArrayAt("candidates")[0]
        .jsonObject
        .jsonObjectAt("content")
        .jsonArrayAt("parts")[0]
        .jsonObject
        .stringAt("text")

    AiEngineType.LOCAL_MODEL -> error("Local model responses are not remote API responses")
  }
}

private fun apiEndpoint(baseUrl: String, path: String): String {
  val base = baseUrl.trim().trimEnd('/')
  return if (base.isBlank()) "" else "$base/$path"
}

private fun authHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) emptyMap() else mapOf("Authorization" to "Bearer ${apiKey.trim()}")

private fun anthropicHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) {
    mapOf("anthropic-version" to "2023-06-01")
  } else {
    mapOf("x-api-key" to apiKey.trim(), "anthropic-version" to "2023-06-01")
  }

internal fun mapRemoteError(error: Throwable): String =
  when (error) {
    is IllegalArgumentException -> "远程 AI Endpoint 无效，请检查配置"
    is java.io.IOException -> error.message ?: "远程 AI 网络请求失败"
    else -> error.message ?: "远程 AI 请求失败"
  }

// --- Provider request/response shapes ---

@Serializable
private data class ChatCompletionRequest(
  val model: String,
  val messages: List<ChatMessage>,
  @SerialName("max_tokens") val maxTokens: Int = 768,
)

@Serializable private data class ChatMessage(val role: String = "user", val content: String)

@Serializable
private data class ResponsesRequest(
  val model: String,
  val input: String,
  @SerialName("max_output_tokens") val maxOutputTokens: Int = 768,
)

@Serializable
private data class AnthropicRequest(
  val model: String,
  @SerialName("max_tokens") val maxTokens: Int = 768,
  val messages: List<ChatMessage>,
)

@Serializable
private data class GeminiRequest(
  val contents: List<GeminiContent>,
  val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig(),
)

@Serializable private data class GeminiContent(val parts: List<GeminiPart>)

@Serializable private data class GeminiPart(val text: String)

@Serializable
private data class GeminiGenerationConfig(
  @SerialName("maxOutputTokens") val maxOutputTokens: Int = 768,
)

// --- Small JsonElement helpers (tolerant navigation) ---

private fun JsonObject.jsonArrayAt(key: String) = this[key]!!.jsonArray

private fun JsonObject.jsonObjectAt(key: String) = this[key]!!.jsonObject

private fun JsonObject.stringAt(key: String) = this[key]!!.jsonPrimitive.content

private fun JsonObject.stringOrNull(key: String) = this[key]?.jsonPrimitive?.contentOrNull
