package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.sixMinistryTopics
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Port of the Android RemoteApiSmartSummarizerTest.
 *
 * These exercise the prompt/parse pipeline through the [RemoteAiTransport] seam, so they run on any
 * target without network access.
 */
class RemoteApiSmartSummarizerTest {
  private val topics: List<Topic> = sixMinistryTopics
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  private fun openAiSettings() =
    AiEngineSettings(
      engineType = AiEngineType.OPENAI_COMPATIBLE,
      baseUrl = "https://api.example.com/v1",
      modelName = "test-model",
      apiKey = "sk-test",
    )

  private fun successJson(
    topicId: String = "rites",
    contentType: String = "WEB_ARTICLE",
    title: String = "测试标题",
    summary: String = "测试摘要",
    documentFormat: String = "PDF",
    sourceUrl: String = "https://example.com/a",
  ) =
    """{"topicId":"$topicId","contentType":"$contentType","title":"$title","summary":"$summary","documentFormat":"$documentFormat","sourceUrl":"$sourceUrl"}"""

  /** Wraps a raw string as a properly escaped JSON string value. */
  private fun asJsonString(value: String): String = json.encodeToString(value)

  /**
   * Wraps a model payload in the OpenAI-compatible response envelope.
   *
   * [RemoteApiSmartSummarizer] always routes the raw response through
   * [extractModelText], so a bare JSON object passed as the HTTP body would never parse — the
   * settings under test are OPENAI_COMPATIBLE and expect `choices[0].message.content`.
   */
  private fun openAiEnvelope(payload: String): String =
    """{"choices":[{"message":{"content":${asJsonString(payload)}}}]}"""

  private suspend fun summarizeWith(
    payload: String,
    code: Int = 200,
    settings: AiEngineSettings = openAiSettings(),
  ): SmartSummarizeResult {
    val transport =
      object : RemoteAiTransport {
        override suspend fun send(request: RemoteAiRequest) =
          RemoteAiResponse(code, openAiEnvelope(payload))
      }
    return RemoteApiSmartSummarizer(settings, transport)
      .summarize(SmartSummarizeRequest(rawText = "一段原始输入"), topics)
  }

  private fun run(block: suspend () -> Unit) = runBlocking { block() }

  @Test
  fun `parses strict json into success`() = run {
    val result = summarizeWith(successJson())
    assertTrue(result is SmartSummarizeResult.Success, "expected Success, got $result")
    val success = result as SmartSummarizeResult.Success
    assertEquals("rites", success.topicId)
    assertEquals(ContentType.WEB_ARTICLE, success.contentType)
    assertEquals("测试标题", success.title)
    assertEquals(DocumentFormat.PDF, success.documentFormat)
    assertEquals("https://example.com/a", success.sourceUrl)
  }

  @Test
  fun `tolerates markdown fenced json`() = run {
    val result = summarizeWith("```json\n" + successJson() + "\n```")
    assertTrue(result is SmartSummarizeResult.Success, "expected Success, got $result")
  }

  @Test
  fun `tolerates prose around json`() = run {
    val result = summarizeWith("好的，结果如下：\n" + successJson() + "\n希望有帮助")
    assertTrue(result is SmartSummarizeResult.Success, "expected Success, got $result")
  }

  @Test
  fun `blank sourceUrl normalizes to null`() = run {
    val result = summarizeWith(successJson(sourceUrl = "   "))
    assertNull((result as SmartSummarizeResult.Success).sourceUrl)
  }

  @Test
  fun `unknown topicId falls back to treasury`() = run {
    val result = summarizeWith(successJson(topicId = "not-a-ministry"))
    assertEquals("treasury", (result as SmartSummarizeResult.Success).topicId)
  }

  @Test
  fun `ALL contentType is rejected`() = run {
    val result = summarizeWith(successJson(contentType = "ALL"))
    assertTrue(result is SmartSummarizeResult.Failure, "expected Failure, got $result")
  }

  @Test
  fun `blank title is rejected`() = run {
    val result = summarizeWith(successJson(title = "  "))
    assertTrue(result is SmartSummarizeResult.Failure, "expected Failure, got $result")
  }

  @Test
  fun `http error surfaces code`() = run {
    val result = summarizeWith("{}", code = 401)
    assertTrue(
      (result as SmartSummarizeResult.Failure).message.contains("401"),
      "message was: ${result.message}",
    )
  }

  @Test
  fun `blank input short circuits before network`() = run {
    var called = false
    val transport =
      object : RemoteAiTransport {
        override suspend fun send(request: RemoteAiRequest): RemoteAiResponse {
          called = true
          return RemoteAiResponse(200, successJson())
        }
      }
    val result =
      RemoteApiSmartSummarizer(openAiSettings(), transport)
        .summarize(SmartSummarizeRequest(rawText = "   "), topics)
    assertTrue(result is SmartSummarizeResult.Failure, "expected Failure, got $result")
    assertTrue(!called, "transport must not be called for blank input")
  }

  @Test
  fun `empty endpoint is reported`() = run {
    val result = summarizeWith(successJson(), settings = openAiSettings().copy(baseUrl = ""))
    assertTrue(
      (result as SmartSummarizeResult.Failure).message.contains("Endpoint"),
      "message was: ${result.message}",
    )
  }

  @Test
  fun `openai compatible response text is extracted`() {
    val body = """{"choices":[{"message":{"content":${asJsonString(successJson())}}}]}"""
    assertEquals(successJson(), extractModelText(AiEngineType.OPENAI_COMPATIBLE, body))
  }

  @Test
  fun `anthropic response text is extracted`() {
    val body = """{"content":[{"text":${asJsonString(successJson())}}]}"""
    assertEquals(successJson(), extractModelText(AiEngineType.ANTHROPIC, body))
  }

  @Test
  fun `gemini response text is extracted`() {
    val body =
      """{"candidates":[{"content":{"parts":[{"text":${asJsonString(successJson())}}]}}]}"""
    assertEquals(successJson(), extractModelText(AiEngineType.GEMINI, body))
  }

  @Test
  fun `responses api output_text is preferred`() {
    val body = """{"output_text":${asJsonString(successJson())}}"""
    assertEquals(successJson(), extractModelText(AiEngineType.OPENAI_RESPONSES, body))
  }

  @Test
  fun `responses api falls back to output array`() {
    val body = """{"output":[{"content":[{"text":${asJsonString(successJson())}}]}]}"""
    assertEquals(successJson(), extractModelText(AiEngineType.OPENAI_RESPONSES, body))
  }
}
