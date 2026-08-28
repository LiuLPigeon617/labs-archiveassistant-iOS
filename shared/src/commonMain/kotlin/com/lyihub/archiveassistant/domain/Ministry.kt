package com.lyihub.archiveassistant.domain

/**
 * The six canonical ministries (六部) that serve as the immutable topic taxonomy.
 *
 * Migrated verbatim from the Android `Models.kt`. This enum is the backbone of the product and is
 * intentionally free of any platform dependency.
 */
enum class SixMinistry(
  val id: String,
  val order: Int,
  val label: String,
) {
  OFFICIALS("officials", 0, "吏 · 名籍"),
  TREASURY("treasury", 1, "户 · 府库"),
  RITES("rites", 2, "礼 · 典章"),
  MILITARY("military", 3, "兵 · 行令"),
  JUSTICE("justice", 4, "刑 · 稽核"),
  WORKS("works", 5, "工 · 营造");

  companion object {
    fun byId(id: String): SixMinistry? = entries.firstOrNull { it.id == id }
  }
}

fun SixMinistry.toTopic(): Topic =
  Topic(
    id = id,
    title = label,
    iconName = "folder-spark",
    iconColor = "#5e5d59",
    updatedAtEpochMillis = 1_715_000_000_000,
  )

val sixMinistryTopics: List<Topic> = SixMinistry.entries.map { it.toTopic() }

fun resolveTopicId(topicId: String?): String =
  SixMinistry.byId(topicId.orEmpty())?.id ?: SixMinistry.TREASURY.id

data class Topic(
  val id: String,
  val title: String,
  val iconName: String,
  val iconColor: String,
  val updatedAtEpochMillis: Long,
)

data class KnowledgeItem(
  val id: String,
  val topicId: String,
  val contentType: ContentType,
  val title: String,
  val summary: String,
  val fullText: String,
  val sourceUrl: String?,
  val imageResName: String? = null,
  val documentFormat: DocumentFormat? = null,
  val fileName: String? = null,
  val fileSize: Long? = null,
  val createdAtEpochMillis: Long,
)

enum class ContentType(val label: String) {
  ALL("全部"),
  WEB_ARTICLE("网页"),
  IMAGE_SCREENSHOT("图像"),
  DOCUMENT("文档"),
}

enum class DocumentFormat(val label: String, val extension: String) {
  PDF("PDF", ".pdf"),
  MARKDOWN("Markdown", ".md"),
  TXT("纯文本", ".txt"),
  DOCX("Word", ".docx"),
  UNKNOWN("未知文档", ""),
}
