package com.lyihub.archiveassistant.domain

import kotlinx.serialization.Serializable

/** Web page context that can optionally accompany a [SmartSummarizeRequest]. */
@Serializable
data class FetchedWebContext(
  val originalUrl: String,
  val title: String,
  val description: String,
  val bodyText: String,
)

@Serializable
data class FetchedDocumentContext(
  val fileName: String,
  val format: DocumentFormat,
  val extractedText: String,
  val originalCharCount: Int,
  val isTruncated: Boolean,
)

/**
 * Request for smart summarization. Carries only original user/clipboard input — never model output.
 */
@Serializable
data class SmartSummarizeRequest(
  val rawText: String,
  val sourceUrl: String? = null,
  val sourceTitle: String? = null,
  val fetchedWebContext: FetchedWebContext? = null,
  val fetchedDocumentContext: FetchedDocumentContext? = null,
)

/** Result from smart summarization. */
sealed interface SmartSummarizeResult {
  /**
   * Successful summarization with fields parsed from AI JSON output.
   *
   * @param sourceUrl Source URL from AI; an empty string is normalized to null.
   */
  data class Success(
    val topicId: String,
    val contentType: ContentType,
    val title: String,
    val summary: String,
    val documentFormat: DocumentFormat,
    val sourceUrl: String? = null,
  ) : SmartSummarizeResult {
    fun toClassificationPayload(rawText: String): ClassificationPayload =
      ClassificationPayload(
        topicId = topicId,
        contentType = contentType,
        title = title,
        summary = summary,
        rawInput = rawText,
        documentFormat = documentFormat,
      )

    companion object {
      fun fromAiJson(
        topicId: String,
        contentType: ContentType,
        title: String,
        summary: String,
        documentFormat: DocumentFormat,
        sourceUrl: String? = null,
      ): Success =
        Success(
          topicId = topicId,
          contentType = contentType,
          title = title,
          summary = summary,
          documentFormat = documentFormat,
          sourceUrl = if (sourceUrl.isNullOrBlank()) null else sourceUrl,
        )
    }
  }

  /** Failed summarization with a user-visible message and no partial payload. */
  data class Failure(val message: String) : SmartSummarizeResult
}

/**
 * Contract for smart summarization.
 *
 * Implementations produce a structured [SmartSummarizeResult] from user input without persisting
 * new models or modifying [KnowledgeItem] fields.
 */
interface SmartSummarizer {
  suspend fun summarize(
    request: SmartSummarizeRequest,
    topics: List<Topic>,
    existingItems: List<KnowledgeItem> = emptyList(),
  ): SmartSummarizeResult
}
