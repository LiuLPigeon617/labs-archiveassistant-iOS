package com.lyihub.archiveassistant.state

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AppPane
import com.lyihub.archiveassistant.domain.BenchResult
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.resolveTopicId

enum class TopicNameDialogMode {
  CREATE,
  RENAME,
}

data class AddItemDialogPrefill(
  val title: String = "",
  val contentType: ContentType = ContentType.WEB_ARTICLE,
  val sourceUrl: String? = null,
  val documentFormat: DocumentFormat? = null,
  val fileName: String? = null,
  val lockContentType: Boolean = false,
  val availableContentTypes: List<ContentType>? = null,
  val textContent: String? = null,
)

data class ClipboardSnapshot(
  val content: String? = null,
  val imageUri: String? = null,
  val sourceUri: String? = null,
  val sourceContentType: ContentType? = null,
  val sourceDocumentFormat: DocumentFormat? = null,
  val sourceFileName: String? = null,
  val sourceLabel: String? = null,
)

data class ArchiveAssistantState(
  val selectedPane: AppPane = AppPane.TOPICS,
  val selectedTopicId: String? = null,
  val topics: List<Topic> = emptyList(),
  val items: List<KnowledgeItem> = emptyList(),
  val activeDetailFilter: ContentType = ContentType.ALL,
  val parserInput: String = "",
  val parserValidationMessage: String? = null,
  val isSmartSummarizing: Boolean = false,
  val smartSummarizationMessage: String? = null,
  val topicValidationMessage: String? = null,
  val modalItem: KnowledgeItem? = null,
  val aiSettings: AiEngineSettings = AiEngineSettings(),
  val localModelState: LocalModelState = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED),
  val benchmarkResult: BenchResult? = null,
  val isBenchmarkRunning: Boolean = false,
  val topicNameDialogMode: TopicNameDialogMode? = null,
  val topicNameDialogTopicId: String? = null,
  val deleteConfirmTopicId: String? = null,
  val addItemDialogVisible: Boolean = false,
  val addItemDialogValidationMessage: String? = null,
  val addItemDialogPrefill: AddItemDialogPrefill? = null,
  val editingItem: KnowledgeItem? = null,
  val editItemDialogValidationMessage: String? = null,
  val deleteConfirmItemId: String? = null,
  val homeSearchQuery: String = "",
  val clipboardContent: String? = null,
  val showClipboardDialog: Boolean = false,
  val clipboardImageUri: String? = null,
  val clipboardSourceUri: String? = null,
  val clipboardSourceContentType: ContentType? = null,
  val clipboardSourceDocumentFormat: DocumentFormat? = null,
  val clipboardSourceFileName: String? = null,
  val clipboardSourceLabel: String? = null,
  val latestClipboardSnapshot: ClipboardSnapshot? = null,
  val ignoredClipboardSnapshot: ClipboardSnapshot? = null,
) {
  val itemsByTopic: Map<String, List<KnowledgeItem>> = items.groupBy { resolveTopicId(it.topicId) }

  private val resolvedSelectedTopicId: String? = selectedTopicId?.let(::resolveTopicId)

  val selectedTopic: Topic? = topics.firstOrNull { it.id == resolvedSelectedTopicId }

  val selectedTopicItems: List<KnowledgeItem> =
    resolvedSelectedTopicId?.let { topicId -> itemsByTopic[topicId].orEmpty() }.orEmpty()

  val filteredSelectedTopicItems: List<KnowledgeItem> =
    when (activeDetailFilter) {
      ContentType.ALL -> selectedTopicItems
      else -> selectedTopicItems.filter { it.contentType == activeDetailFilter }
    }

  val recentTopics: List<Topic> = topics.sortedByDescending { it.updatedAtEpochMillis }

  val searchedTopics: List<Topic> =
    if (homeSearchQuery.isBlank()) {
      recentTopics
    } else {
      val query = homeSearchQuery.lowercase()
      val matchingTopicIds =
        items
          .filter { it.title.lowercase().contains(query) || it.summary.lowercase().contains(query) }
          .map { resolveTopicId(it.topicId) }
          .toSet()
      topics
        .filter { it.title.lowercase().contains(query) || it.id in matchingTopicIds }
        .sortedByDescending { it.updatedAtEpochMillis }
    }
}
