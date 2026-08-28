package com.lyihub.archiveassistant.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class LocalLlmClassifier(private val engine: LocalLlmEngine) {
  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  suspend fun classify(rawInput: String, topics: List<Topic>): ClassificationResult {
    val normalizedInput = rawInput.trim()
    if (normalizedInput.isEmpty()) {
      return ClassificationResult.BlankInput()
    }

    val ministryTopics = sixMinistryTopics
    val output =
      engine
        .generate(promptFor(normalizedInput, ministryTopics))
        .getOrElse { return ClassificationResult.Unknown }
        .trim()
    if (output.isEmpty()) {
      return ClassificationResult.Unknown
    }

    return runCatching { json.decodeFromString<ClassifierOutput>(output) }
      .map { parsed ->
        val topicName = parsed.topic.trim()
        val matchedTopicId =
          topics.firstOrNull { it.title == topicName || it.id == topicName }?.id
            ?: ministryTopics.firstOrNull { it.title == topicName || it.id == topicName }?.id

        ClassificationResult.Classified(
          ClassificationPayload(
            topicId = resolveTopicId(matchedTopicId ?: topicName),
            contentType = ContentType.WEB_ARTICLE,
            title =
              normalizedInput.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(28)
                ?: "提取内容",
            summary = parsed.reason.trim().ifBlank { normalizedInput.take(96) },
            rawInput = normalizedInput,
          )
        )
      }
      .getOrElse { ClassificationResult.Unknown }
  }

  private fun promptFor(rawInput: String, topics: List<Topic>): String =
    """
        你是一个归档助手。根据以下输入内容，从六部主题列表中选择最匹配的主题。
        输入内容：$rawInput
        可选主题：${topics.joinToString(", ") { "${it.id}=${it.title}" }}
        topic 必须返回上述六部 ID 之一：${topics.joinToString(", ") { it.id }}。禁止创建新主题。
        请只返回 JSON，格式：{"topic": "六部ID", "confidence": 0.0-1.0, "reason": "简短理由"}
    """
      .trimIndent()

  @Serializable
  private data class ClassifierOutput(
    val topic: String = "",
    val confidence: Float = 0f,
    val reason: String = "",
  )
}
