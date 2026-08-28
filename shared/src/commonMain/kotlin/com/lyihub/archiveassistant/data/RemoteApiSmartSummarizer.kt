package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.SmartSummarizer
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.extractJsonObject
import com.lyihub.archiveassistant.domain.resolveTopicId
import com.lyihub.archiveassistant.domain.sixMinistryTopics
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class RemoteApiSmartSummarizer(
  private val settings: AiEngineSettings,
  private val transport: RemoteAiTransport = defaultRemoteAiTransport(),
) : SmartSummarizer {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  override suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem>,
  ): SmartSummarizeResult {
    val normalizedInput = request.rawText.trim()
    if (normalizedInput.isEmpty()) {
      return SmartSummarizeResult.Failure("请输入要智能总结的内容")
    }
    if (topics.isEmpty()) {
      return SmartSummarizeResult.Failure("没有可用主题，无法智能总结")
    }

    val prompt = promptFor(request, normalizedInput, sixMinistryTopics)
    val apiRequest = buildRemoteRequest(settings, prompt)
    if (apiRequest.endpoint.isBlank()) {
      return SmartSummarizeResult.Failure("远程 AI Endpoint 为空，请检查配置")
    }

    val response =
      runCatching { transport.send(apiRequest) }.getOrElse { error ->
        return SmartSummarizeResult.Failure(mapRemoteError(error))
      }
    if (response.code !in 200..299) {
      return SmartSummarizeResult.Failure("远程 AI 请求失败：HTTP ${response.code}")
    }

    return runCatching { parseResult(extractModelText(settings.engineType, response.body)) }
      .getOrElse { SmartSummarizeResult.Failure("远程 AI 返回格式无效，请重试") }
  }

  private fun parseResult(output: String): SmartSummarizeResult.Success {
    val parsed = json.decodeFromString<SummaryOutput>(extractJsonObject(output))

    val contentType = requireNotNull(contentTypeValue(parsed.contentType)) { "Invalid contentType" }
    val documentFormat =
      requireNotNull(documentFormatValue(parsed.documentFormat)) { "Invalid documentFormat" }
    require(contentType in ALLOWED_CONTENT_TYPES) { "Disallowed contentType" }
    require(parsed.title.isNotBlank()) { "Empty title" }
    require(parsed.summary.isNotBlank()) { "Empty summary" }

    return SmartSummarizeResult.Success.fromAiJson(
      topicId = resolveTopicId(parsed.topicId),
      contentType = contentType,
      title = parsed.title.trim(),
      summary = parsed.summary.trim(),
      documentFormat = documentFormat,
      sourceUrl = parsed.sourceUrl?.trim(),
    )
  }

  private fun contentTypeValue(value: String): ContentType? =
    ContentType.entries.firstOrNull { type ->
      type.name == value || type.name.equals(value, ignoreCase = true) || type.label == value
    }

  private fun documentFormatValue(value: String): DocumentFormat? =
    DocumentFormat.entries.firstOrNull { format ->
      format.name == value ||
        format.name.equals(value, ignoreCase = true) ||
        format.label == value
    }

  private fun promptFor(
    request: SmartSummarizeRequest,
    normalizedInput: String,
    topics: List<Topic>,
  ): String {
    val fetchedBlock =
      request.fetchedWebContext
        ?.let { ctx ->
          """
已获取的网页内容：
原始 URL：${ctx.originalUrl}
网页标题：${ctx.title}
网页描述：${ctx.description}
网页正文：${ctx.bodyText}

注意：禁止只根据 URL 猜测标题或摘要。
当 contentType 为 WEB_ARTICLE 时，title 必须使用上述网页标题（来自已获取的网页元数据/内容），不要发明摘要式的标题。
summary 必须基于上述网页正文/描述生成。
sourceUrl 必须等于原始 URL。

"""
            .trimIndent()
        }
        .orEmpty()
    val documentBlock =
      request.fetchedDocumentContext
        ?.let { ctx ->
          """
已解析的文档内容：
文件名：${ctx.fileName}
文档格式：${ctx.format.name}
原始字符数：${ctx.originalCharCount}
是否已截断：${ctx.isTruncated}
文档正文节选：${ctx.extractedText}

注意：当 contentType 为 DOCUMENT 时，title 和 summary 必须基于上述文档正文节选生成。
documentFormat 必须等于上述文档格式。
如果“是否已截断”为 true，只能基于可见节选总结，禁止猜测未提供内容。

"""
            .trimIndent()
        }
        .orEmpty()

    val exampleContentType =
      if (request.fetchedDocumentContext != null) "DOCUMENT" else "WEB_ARTICLE"
    val exampleDocumentFormat = request.fetchedDocumentContext?.format?.name ?: "PDF"

    return """
        你是一个归档助手。请只基于用户原始输入进行智能总结。
        你必须根据主题名称从六部主题中选择最接近的一个 topicId，topicId 必须是下列六部 ID 之一，禁止创建新主题或返回主题名称。

        用户原始输入：$normalizedInput
        来源 URL（如有）：${request.sourceUrl.orEmpty()}
        来源标题（如有）：${request.sourceTitle.orEmpty()}

        ${fetchedBlock}${documentBlock}六部主题：
        ${topics.joinToString("\n") { "- id=${it.id}; title=${it.title}" }}

        请推断 contentType、documentFormat 和 sourceUrl（能确定时填写；不能确定时 sourceUrl 返回空字符串）。
        contentType 只能是：${ALLOWED_CONTENT_TYPES.joinToString(", ") { it.name }}。
        documentFormat 只能是：${DocumentFormat.entries.joinToString(", ") { it.name }}；只有模型明确判断未知文档格式时才返回 UNKNOWN。
        title、summary 必须简洁，title 不超过 28 个中文字符，summary 不超过 96 个中文字符。

        只返回严格 JSON 对象，不要 Markdown，不要解释，不要额外字段：
        {"topicId":"六部ID","contentType":"$exampleContentType","title":"简洁标题","summary":"一句话摘要","sourceUrl":"来源URL或空字符串","documentFormat":"$exampleDocumentFormat"}
    """
      .trimIndent()
  }

  @Serializable
  private data class SummaryOutput(
    val topicId: String = "",
    val contentType: String = "",
    val title: String = "",
    val summary: String = "",
    @SerialName("documentFormat") val documentFormat: String = "",
    val sourceUrl: String? = null,
  )

  private companion object {
    val ALLOWED_CONTENT_TYPES =
      setOf(ContentType.WEB_ARTICLE, ContentType.IMAGE_SCREENSHOT, ContentType.DOCUMENT)
  }
}
