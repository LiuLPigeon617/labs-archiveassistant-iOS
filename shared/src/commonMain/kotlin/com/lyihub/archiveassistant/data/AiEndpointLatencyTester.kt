package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Measures round-trip latency to a configured AI endpoint.
 *
 * The Android build used `HttpURLConnection` and `System.nanoTime()`, both JVM-only. This port goes
 * through Ktor (native TLS per platform, same engine as `KtorRemoteAiTransport`) and
 * `TimeSource.Monotonic`, which is available on every target.
 */
sealed class AiEndpointLatencyResult {
  data class Success(val elapsedMillis: Long) : AiEndpointLatencyResult()

  data class Failure(val message: String) : AiEndpointLatencyResult()
}

suspend fun testAiEndpointLatency(
  settings: AiEngineSettings,
  apiKey: String = "",
  transport: AiLatencyTransport = KtorAiLatencyTransport(),
): AiEndpointLatencyResult =
  withContext(Dispatchers.Default) {
    val request = buildLatencyRequest(settings, apiKey)
    if (request.endpoint.isBlank()) {
      return@withContext AiEndpointLatencyResult.Failure("Endpoint is empty")
    }

    val started = TimeSource.Monotonic.markNow()
    val result = runCatching { transport.send(request) }
    val elapsedMillis = started.elapsedNow().inWholeMilliseconds

    result.fold(
      onSuccess = { code ->
        if (code in 200..299) {
          AiEndpointLatencyResult.Success(elapsedMillis)
        } else {
          AiEndpointLatencyResult.Failure("HTTP $code")
        }
      },
      onFailure = { error -> AiEndpointLatencyResult.Failure(mapLatencyError(error)) },
    )
  }

/** Transport seam so the latency path stays unit-testable without touching the network. */
interface AiLatencyTransport {
  suspend fun send(request: AiLatencyRequest): Int
}

/**
 * Ktor-backed [AiLatencyTransport].
 *
 * A non-2xx status is a valid latency measurement, not a transport error, so the response body is
 * discarded and the status code returned as-is.
 */
class KtorAiLatencyTransport(private val client: HttpClient = provideHttpClient()) :
  AiLatencyTransport {
  override suspend fun send(request: AiLatencyRequest): Int =
    client
      .request(request.endpoint) {
        method = HttpMethod.parse(request.method)
        request.headers.forEach { (key, value) -> headers.append(key, value) }
        request.body?.let {
          contentType(ContentType.Application.Json)
          setBody(it)
        }
      }
      .status
      .value
}

data class AiLatencyRequest(
  val endpoint: String,
  val method: String,
  val headers: Map<String, String>,
  val body: String?,
)

internal fun buildLatencyRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest =
  when (settings.engineType) {
    AiEngineType.OPENAI_COMPATIBLE -> openAiCompatibleRequest(settings, apiKey)
    AiEngineType.OPENAI_RESPONSES -> openAiResponsesRequest(settings, apiKey)
    AiEngineType.ANTHROPIC -> anthropicRequest(settings, apiKey)
    AiEngineType.GEMINI -> geminiRequest(settings, apiKey)
    AiEngineType.LOCAL_MODEL -> localModelRequest(settings)
  }

private fun openAiCompatibleRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "chat/completions")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = authHeaders(apiKey),
    body = body,
  )
}

private fun openAiResponsesRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "responses")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "input" to jsonString("hi"),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = authHeaders(apiKey),
    body = body,
  )
}

private fun anthropicRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val endpoint = apiEndpoint(settings.baseUrl, "messages")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "max_tokens" to "1",
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
    )
  val headers = authHeaders(apiKey).plus("anthropic-version" to "2023-06-01")
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = headers,
    body = body,
  )
}

private fun geminiRequest(settings: AiEngineSettings, apiKey: String): AiLatencyRequest {
  val modelPath =
    if (settings.modelName.startsWith("models/")) {
      settings.modelName
    } else {
      "models/${settings.modelName}"
    }
  val endpoint =
    apiEndpoint(settings.baseUrl, "$modelPath:generateContent") + "?key=${apiKey.trim()}"
  val body =
    jsonObject(
      "contents" to
        jsonArray(jsonObject("parts" to jsonArray(jsonObject("text" to jsonString("hi"))))),
      "generationConfig" to jsonObject("maxOutputTokens" to "1"),
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = emptyMap(),
    body = body,
  )
}

/**
 * The Android build read a deprecated `localEndpoint` field (default `http://127.0.0.1:11434`).
 * That field is deliberately absent from the shared [AiEngineSettings]: in-process LiteRT-LM has no
 * HTTP daemon. Loopback latency is still meaningful during migration, so the endpoint is derived
 * from `baseUrl` when it already looks like a local address, and otherwise falls back to the
 * historical Ollama default.
 */
private fun localModelRequest(settings: AiEngineSettings): AiLatencyRequest {
  val endpoint = apiEndpoint(localLatencyBaseUrl(settings), "v1/chat/completions")
  val body =
    jsonObject(
      "model" to jsonString(settings.modelName),
      "messages" to
        jsonArray(
          jsonObject(
            "role" to jsonString("user"),
            "content" to jsonString("hi"),
          )
        ),
      "max_tokens" to "1",
    )
  return AiLatencyRequest(
    endpoint = endpoint,
    method = "POST",
    headers = emptyMap(),
    body = body,
  )
}

private const val DefaultLocalLatencyBaseUrl = "http://127.0.0.1:11434"

private fun localLatencyBaseUrl(settings: AiEngineSettings): String {
  val base = settings.baseUrl.trim()
  val looksLocal =
    base.contains("127.0.0.1") || base.contains("localhost") || base.contains("0.0.0.0")
  return if (looksLocal) base else DefaultLocalLatencyBaseUrl
}

private fun apiEndpoint(baseUrl: String, path: String): String {
  val base = baseUrl.trim().trimEnd('/')
  return if (base.isBlank()) "" else "$base/$path"
}

private fun authHeaders(apiKey: String): Map<String, String> =
  if (apiKey.isBlank()) {
    emptyMap()
  } else {
    mapOf("Authorization" to "Bearer $apiKey")
  }

private fun jsonString(value: String): String =
  "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

private fun jsonObject(vararg pairs: Pair<String, String>): String =
  pairs.joinToString(",", "{", "}") { "${jsonString(it.first)}:${it.second}" }

private fun jsonArray(vararg items: String): String = items.joinToString(",", "[", "]")

private fun mapLatencyError(error: Throwable): String =
  error.message?.takeIf { it.isNotBlank() } ?: "Request failed"
