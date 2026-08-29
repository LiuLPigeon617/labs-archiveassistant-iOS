package com.lyihub.archiveassistant.domain

/**
 * Deterministic keyword-based classifier.
 *
 * Used when no AI engine is configured, and as the fallback in tests. Migrated verbatim: it is pure
 * string matching with no platform dependency.
 */
class MockKnowledgeClassifier(private val defaultTopics: List<Topic> = SampleKnowledgeData.topics) {
  fun classify(rawInput: String, topics: List<Topic> = defaultTopics): ClassificationResult {
    val normalizedInput = rawInput.trim()
    if (normalizedInput.isEmpty()) {
      return ClassificationResult.BlankInput()
    }

    val contentType = detectContentType(normalizedInput)
    val documentFormat =
      if (contentType == ContentType.DOCUMENT) {
        detectDocumentFormat(normalizedInput)
      } else null
    val topic = selectTopic(normalizedInput, topics)

    return ClassificationResult.Classified(
      ClassificationPayload(
        topicId = topic.id,
        contentType = contentType,
        title = titleFor(normalizedInput),
        summary = normalizedInput.take(96),
        rawInput = normalizedInput,
        documentFormat = documentFormat,
      )
    )
  }

  private fun detectDocumentFormat(input: String): DocumentFormat {
    val lowerInput = input.lowercase()
    return when {
      ".pdf" in lowerInput || lowerInput.contains("pdf") -> DocumentFormat.PDF
      ".md" in lowerInput || lowerInput.contains("markdown") -> DocumentFormat.MARKDOWN
      ".docx" in lowerInput || lowerInput.contains("word") -> DocumentFormat.DOCX
      ".txt" in lowerInput || lowerInput.contains("纯文本") -> DocumentFormat.TXT
      else -> DocumentFormat.UNKNOWN
    }
  }

  private fun detectContentType(input: String): ContentType {
    val lowerInput = input.lowercase()
    return when {
      imageTerms.any { it in lowerInput } -> ContentType.IMAGE_SCREENSHOT
      documentTerms.any { it in lowerInput } -> ContentType.DOCUMENT
      urlTerms.any { it in lowerInput } || urlRegex.containsMatchIn(input) ->
        ContentType.WEB_ARTICLE
      else -> ContentType.WEB_ARTICLE
    }
  }

  private fun selectTopic(input: String, topics: List<Topic>): Topic {
    val lowerInput = input.lowercase()
    val preferredLabel =
      when {
        listOf("ui", "ux", "design", "interface", "界面", "交互", "设计").any { it in lowerInput } ->
          SixMinistry.WORKS.label
        listOf("anthropology", "fieldwork", "人类学", "田野", "仪式").any { it in lowerInput } ->
          SixMinistry.RITES.label
        listOf("travel", "trip", "旅行", "目的地").any { it in lowerInput } ->
          SixMinistry.TREASURY.label
        else -> SixMinistry.OFFICIALS.label
      }
    return topics
      .firstOrNull { it.title == preferredLabel }
      ?.let { it.copy(id = resolveTopicId(it.id)) }
      ?: SixMinistry.byId(resolveTopicId(null))!!.toTopic()
  }

  private fun titleFor(input: String): String =
    input.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(28) ?: "提取内容"

  private companion object {
    val urlRegex = Regex("""https?://\S+|www\.\S+""")
    val urlTerms = listOf("url", "http", "article", "web", "link", "网页", "文章", "链接")
    val imageTerms =
      listOf(
        "image",
        "screenshot",
        "screen shot",
        "photo",
        "png",
        "jpg",
        "jpeg",
        "图片",
        "图像",
        "截图",
        "截屏",
      )
    val documentTerms =
      listOf(
        "pdf",
        "document",
        "doc",
        "docx",
        "paper",
        "report",
        "txt",
        ".txt",
        ".md",
        ".docx",
        ".doc",
        "markdown",
        "word",
        "纯文本",
        "文档",
        "论文",
        "报告",
      )
  }
}
