@file:OptIn(kotlin.time.ExperimentalTime::class)

package com.lyihub.archiveassistant.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lyihub.archiveassistant.data.AiEngineSettingsRepository
import com.lyihub.archiveassistant.data.AppDataPreferences
import com.lyihub.archiveassistant.data.AppDataRepository
import com.lyihub.archiveassistant.data.DefaultWebPageContentFetcher
import com.lyihub.archiveassistant.data.DocumentContentExtractionResult
import com.lyihub.archiveassistant.data.DocumentContentExtractor
import com.lyihub.archiveassistant.data.ModelDownloadManager
import com.lyihub.archiveassistant.data.RemoteApiSmartSummarizer
import com.lyihub.archiveassistant.data.WebPageContentFetchResult
import com.lyihub.archiveassistant.data.WebPageContentFetcher
import com.lyihub.archiveassistant.data.writeMarkdownFile
import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.AppPane
import com.lyihub.archiveassistant.domain.ClassificationResult
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.FetchedDocumentContext
import com.lyihub.archiveassistant.domain.FetchedWebContext
import com.lyihub.archiveassistant.domain.GEMMA_4_E4B_IT
import com.lyihub.archiveassistant.domain.InferenceBackend
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.LocalLlmClassifier
import com.lyihub.archiveassistant.domain.LocalLlmEngine
import com.lyihub.archiveassistant.domain.LocalLlmSmartSummarizer
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.domain.LocalModelStatus
import com.lyihub.archiveassistant.domain.MockKnowledgeClassifier
import com.lyihub.archiveassistant.domain.SampleKnowledgeData
import com.lyihub.archiveassistant.domain.SmartSummarizeRequest
import com.lyihub.archiveassistant.domain.SmartSummarizeResult
import com.lyihub.archiveassistant.domain.SmartSummarizer
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.domain.WebUrlDetector
import com.lyihub.archiveassistant.domain.resolveTopicId
import com.lyihub.archiveassistant.domain.sixMinistryTopics
import com.lyihub.archiveassistant.service.LocalInferenceGateway
import com.lyihub.archiveassistant.platform.BundledAssetReader
import com.lyihub.archiveassistant.platform.ContentSource
import com.lyihub.archiveassistant.platform.Logger
import com.lyihub.archiveassistant.platform.PlatformFileStore
import com.lyihub.archiveassistant.platform.platformFileStore
import com.lyihub.archiveassistant.platform.platformLogger
import com.lyihub.archiveassistant.platform.resolveContentSource
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ArchiveAssistantStateStore(
  private val classifier: MockKnowledgeClassifier = MockKnowledgeClassifier(),
  private val initialState: ArchiveAssistantState =
    ArchiveAssistantState(
      topics = SampleKnowledgeData.topics,
      items = SampleKnowledgeData.items,
      aiSettings = SampleKnowledgeData.defaultAiEngineSettings,
    ),
  private val appDataRepository: AppDataRepository? = null,
  private val aiSettingsRepository: AiEngineSettingsRepository? = null,
  private val localLlmEngine: LocalLlmEngine? = null,
  private val modelDownloadManager: ModelDownloadManager? = null,
  private val inferenceConnection: LocalInferenceGateway? = null,
  private val localModelStateProvider: (() -> LocalModelState)? = null,
  private val localModelFileExists: (() -> Boolean)? = null,
  smartSummarizer: SmartSummarizer? = null,
  private val remoteSmartSummarizerFactory: (AiEngineSettings) -> SmartSummarizer =
    ::RemoteApiSmartSummarizer,
  private val webPageContentFetcher: WebPageContentFetcher = DefaultWebPageContentFetcher(),
  documentContentExtractor: DocumentContentExtractor? = null,
  private val fileStore: PlatformFileStore = platformFileStore(),
  private val assetReader: BundledAssetReader = BundledAssetReader(),
  private val logger: Logger = platformLogger(),
  val itemsDirProvider: (() -> String)? = { fileStore.itemsDir },
) {
  private val documentContentExtractor = documentContentExtractor
  private val scope = CoroutineScope(Dispatchers.Default)
  private val smartSummarizer: SmartSummarizer? =
    smartSummarizer ?: localLlmEngine?.let(::LocalLlmSmartSummarizer)

  // Seeded with the raw initial state. Bundled seed assets are materialized asynchronously in
  // init, because BundledAssetReader.materialize is suspend and cannot run in a field initializer.
  var state: ArchiveAssistantState by mutableStateOf(initialState)
    private set

  var releaseDragPermission: (() -> Unit)? = null

  private var nextTopicIndex = deriveNextTopicIndex(state.topics)
  private var nextItemIndex = deriveNextItemIndex(state.items)

  init {
    loadPersistedStateAsync()
    materializeSeedAssets()
    restoreLocalModelStateAsync()
    observeLocalModelState()
  }

  /**
   * Copies bundled seed assets into private storage, then repoints items at their local paths.
   * Runs asynchronously because [BundledAssetReader.materialize] is suspend.
   */
  private fun materializeSeedAssets() {
    scope.launch {
      val resolved = resolveMockResourcePaths(state)
      if (resolved.items != state.items) state = resolved
    }
  }

  private fun restoreLocalModelStateAsync() {
    scope.launch { restoreLocalModelState() }
  }

  private fun loadPersistedStateAsync() {
    val repo = appDataRepository ?: return
    scope.launch {
      val snapshot = repo.loadSnapshot() ?: return@launch
      if (snapshot.topics is AppDataPreferences.DecodeResult.Corrupt) return@launch

      val persistedTopics =
        when (snapshot.topics) {
          is AppDataPreferences.DecodeResult.Valid -> sixMinistryTopics
          AppDataPreferences.DecodeResult.Missing -> sixMinistryTopics
          is AppDataPreferences.DecodeResult.Corrupt -> return@launch
        }
      val storedItems =
        when (val items = snapshot.items) {
          is AppDataPreferences.DecodeResult.Valid -> normalizeItemTopicIds(items.value)
          AppDataPreferences.DecodeResult.Missing -> emptyList()
          is AppDataPreferences.DecodeResult.Corrupt -> emptyList()
        }
      val persistedItems = mergeBuiltInSampleItems(storedItems)
      if (persistedTopics.isEmpty() && persistedItems.isEmpty()) return@launch

      val restoredState =
        resolveMockResourcePaths(
          state.copy(
            topics = persistedTopics,
            items = persistedItems,
          )
        )
      state = restoredState
      nextTopicIndex = deriveNextTopicIndex(restoredState.topics)
      nextItemIndex = deriveNextItemIndex(restoredState.items)
      if (persistedItems != storedItems) {
        persistData(persistedTopics, persistedItems)
      }
    }
  }

  private fun saveData() {
    val repo = appDataRepository ?: return
    scope.launch {
      repo.saveAll(state.topics, state.items)
    }
  }

  private suspend fun persistData(topics: List<Topic>, items: List<KnowledgeItem>) {
    appDataRepository?.saveAll(topics, items)
  }

  private fun normalizeItemTopicIds(items: List<KnowledgeItem>): List<KnowledgeItem> {
    return items.map { item ->
      val resolvedTopicId = resolveTopicId(item.topicId)
      if (item.topicId == resolvedTopicId) item else item.copy(topicId = resolvedTopicId)
    }
  }

  private fun mergeBuiltInSampleItems(items: List<KnowledgeItem>): List<KnowledgeItem> {
    val builtInSamplesById = SampleKnowledgeData.items.associateBy { it.id }
    val updatedItems = items.map { item ->
      val builtInSample = builtInSamplesById[item.id]
      if (builtInSample != null && item.imageResName != builtInSample.imageResName) {
        item.copy(imageResName = builtInSample.imageResName)
      } else {
        item
      }
    }
    val existingIds = items.mapTo(mutableSetOf()) { it.id }
    val missingSamples = SampleKnowledgeData.items.filter { existingIds.add(it.id) }
    return updatedItems + missingSamples
  }

  private fun saveAiSettings() {
    val repo = aiSettingsRepository ?: return
    val settings = state.aiSettings
    scope.launch {
      repo.save(settings)
    }
  }

  private suspend fun restoreLocalModelState() {
    if (state.aiSettings.localModelId != GEMMA_4_E4B_IT.id) {
      logger.w(TAG, "模型版本已变更，请重新下载")
      resetLocalModelState()
      return
    }

    val modelPath = localModelPath()
    val modelExists = isLocalModelFilePresent()
    val restoredState =
      when {
        modelExists ->
          LocalModelState(
            status = LocalModelStatus.DOWNLOADED,
            downloadProgress = 1f,
            downloadBytes = GEMMA_4_E4B_IT.sizeBytes,
            totalBytes = GEMMA_4_E4B_IT.sizeBytes,
            modelPath = modelPath,
          )
        else -> LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED)
      }
    state = state.copy(localModelState = restoredState)
  }

  private fun observeLocalModelState() {
    modelDownloadManager?.let { manager ->
      scope.launch {
        manager.downloadState.collectLatest { updateDownloadModelState(it) }
      }
    }
    inferenceConnection?.let { connection ->
      scope.launch {
        connection.serviceState.collectLatest { updateInferenceModelState(it) }
      }
    }
  }

  private fun updateDownloadModelState(nextState: LocalModelState) {
    if (
      state.localModelState.status in setOf(LocalModelStatus.DOWNLOADED, LocalModelStatus.READY) &&
        nextState.status == LocalModelStatus.NOT_DOWNLOADED
    ) {
      return
    }
    if (
      state.localModelState.status in serviceOwnedStatuses &&
        nextState.status in setOf(LocalModelStatus.NOT_DOWNLOADED, LocalModelStatus.DOWNLOADED)
    ) {
      return
    }
    updateLocalModelState(nextState)
  }

  private fun updateInferenceModelState(nextState: LocalModelState) {
    updateLocalModelState(nextState)
  }

  private fun updateLocalModelState(nextState: LocalModelState) {
    val current = state.localModelState
    if (
      nextState.status == LocalModelStatus.DOWNLOADED &&
        current.status in
          setOf(
            LocalModelStatus.NOT_DOWNLOADED,
            LocalModelStatus.DOWNLOADING,
            LocalModelStatus.ERROR,
          ) &&
        modelDownloadManager?.isModelPresent(GEMMA_4_E4B_IT) != true
    ) {
      return
    }
    state = state.copy(localModelState = nextState)
  }

  private val serviceOwnedStatuses =
    setOf(
      LocalModelStatus.INITIALIZING,
      LocalModelStatus.READY,
      LocalModelStatus.INFERENCING,
      LocalModelStatus.STOPPING,
    )

  /**
   * Materializes bundled seed assets and points items at their local paths.
   *
   * Replaces the Android implementation that copied 23 assets out of res/raw and res/drawable via
   * getIdentifier + openRawResource. The drawable/raw discriminator is dropped because bundling
   * differs per platform: iOS resolves everything through NSBundle.
   */
  private suspend fun resolveMockResourcePaths(
    state: ArchiveAssistantState
  ): ArchiveAssistantState {
    val mapping =
      mapOf(
        "item-transformer-diagram" to
          Pair("transformer_architecture.png", "transformer_architecture.png"),
        "item-justice-sspai-bill-screenshots" to
          Pair("mock_alipay_sticker_noise.png", "mock_alipay_sticker_noise.png"),
        "item-treasury-python-bill-report" to
          Pair("mock_alipay_wechat_finance.md", "alipay-wechat-finance.md"),
        "item-treasury-bill-books" to Pair("mock_bill_books_readme.md", "Bill_books_README.md"),
        "item-rites-d2l" to Pair("mock_d2l_zh_pytorch.pdf", "d2l-zh-pytorch.pdf"),
        "item-rites-pumpkin-book" to Pair("mock_pumpkin_book.pdf", "pumpkin-book.pdf"),
        "item-military-health-coach" to
          Pair("mock_health_coach_readme.md", "health-coach_README.md"),
        "item-military-ai-health-vault" to
          Pair("mock_ai_health_vault_readme_cn.md", "ai-health-vault_README_CN.md"),
        "item-justice-bill-filter-rules" to
          Pair("mock_bill_filter_rules.md", "bill-filter-rules.md"),
        "item-works-health-agent-repo" to
          Pair("mock_xiaoka_health_agent_readme.md", "xiaoka-health-agent_README.md"),
        "item-officials-contact-card-screenshot" to
          Pair("mock_supplier_contact_card.png", "supplier-contact-card.png"),
        "item-officials-team-roster" to
          Pair("mock_studio_team_roster.md", "studio-team-roster.md"),
        "item-treasury-cloud-api-bill" to Pair("mock_cloud_api_bill.png", "cloud-api-bill.png"),
        "item-treasury-quarterly-budget" to
          Pair("mock_studio_quarterly_budget.md", "studio-quarterly-budget.md"),
        "item-rites-nvidia-blackwell" to
          Pair("mock_nvidia_blackwell_architecture.pdf", "nvidia-blackwell-architecture.pdf"),
        "item-rites-moe-paper" to
          Pair("mock_switch_transformer_moe.pdf", "switch-transformer-moe.pdf"),
        "item-rites-hdr-color-standard" to
          Pair("mock_hdr_color_management_notes.md", "hdr-color-management-notes.md"),
        "item-military-smartwatch-weekly" to
          Pair("mock_smartwatch_weekly_health.png", "smartwatch-weekly-health.png"),
        "item-military-zep-memory-notes" to
          Pair("mock_zep_memory_prompt_notes.md", "zep-memory-prompt-notes.md"),
        "item-justice-gb-headlamp-standard" to
          Pair("mock_gb_19152_2025_headlamp.pdf", "gb-19152-2025-headlamp.pdf"),
        "item-justice-ai-hackathon-rules" to
          Pair("mock_ai_hackathon_rules.md", "ai-hackathon-rules.md"),
        "item-justice-low-confidence-quarantine" to
          Pair("mock_low_confidence_quarantine.md", "low-confidence-quarantine.md"),
        "item-works-vivo-fold-script" to
          Pair("mock_vivo_x_fold_video_script.md", "vivo-x-fold-video-script.md"),
      )

    return state.copy(
      items =
        state.items.map { item ->
          val (assetName, outputFileName) = mapping[item.id] ?: return@map item
          val localPath = assetReader.materialize(assetName, outputFileName) ?: return@map item
          item.copy(sourceUrl = localPath)
        }
    )
  }

  private fun deriveNextTopicIndex(topics: List<Topic>): Int {
    var maxIdx = -1
    topics.forEach { topic ->
      val parts = topic.id.removePrefix("topic-").split("-")
      if (parts.size >= 2) parts.last().toIntOrNull()?.let { if (it > maxIdx) maxIdx = it }
    }
    return (if (maxIdx >= 0) maxIdx else topics.size) + 1
  }

  private fun deriveNextItemIndex(items: List<KnowledgeItem>): Int {
    var maxIdx = -1
    items.forEach { item ->
      val parts = item.id.removePrefix("item-").split("-")
      if (parts.size >= 2) parts.last().toIntOrNull()?.let { if (it > maxIdx) maxIdx = it }
    }
    return (if (maxIdx >= 0) maxIdx else items.size) + 1
  }

  fun closePanes() {
    state =
      state.copy(
        selectedPane = AppPane.TOPICS,
        selectedTopicId = null,
        modalItem = null,
        activeDetailFilter = ContentType.ALL,
      )
  }

  fun openSettings() {
    state = state.copy(selectedPane = AppPane.SETTINGS, modalItem = null)
  }

  fun openMemorialBriefing() {
    state = state.copy(selectedPane = AppPane.MEMORIAL, modalItem = null)
  }

  fun openTopicManagement() {
    state = state.copy(selectedPane = AppPane.MANAGE, selectedTopicId = null, modalItem = null)
  }

  fun openTopicManagementForCreate() {
    state =
      state.copy(
        selectedPane = AppPane.MANAGE,
        selectedTopicId = null,
        modalItem = null,
        topicNameDialogMode = null,
        topicNameDialogTopicId = null,
        topicValidationMessage = TOPIC_CRUD_DISABLED_MESSAGE,
      )
  }

  fun openCreateTopicDialog() {
    state =
      state.copy(
        topicNameDialogMode = null,
        topicNameDialogTopicId = null,
        topicValidationMessage = TOPIC_CRUD_DISABLED_MESSAGE,
      )
  }

  fun openRenameTopicDialog(topicId: String) {
    state =
      state.copy(
        topicNameDialogMode = null,
        topicNameDialogTopicId = null,
        topicValidationMessage = TOPIC_CRUD_DISABLED_MESSAGE,
      )
  }

  fun closeTopicNameDialog() {
    state =
      state.copy(
        topicNameDialogMode = null,
        topicNameDialogTopicId = null,
        topicValidationMessage = null,
      )
  }

  fun confirmCreateTopic(title: String) {
    createTopic(title)
    if (state.topicValidationMessage == null) {
      closeTopicNameDialog()
    }
  }

  fun confirmRenameTopic(title: String) {
    val topicId = state.topicNameDialogTopicId ?: return
    renameTopic(topicId, title)
    if (state.topicValidationMessage == null) {
      closeTopicNameDialog()
    }
  }

  fun openDeleteConfirmDialog(topicId: String) {
    state =
      state.copy(
        deleteConfirmTopicId = null,
        topicValidationMessage = TOPIC_CRUD_DISABLED_MESSAGE,
      )
  }

  fun closeDeleteConfirmDialog() {
    state = state.copy(deleteConfirmTopicId = null)
  }

  fun openAddItemDialog() {
    state =
      state.copy(
        addItemDialogVisible = true,
        addItemDialogValidationMessage = null,
        addItemDialogPrefill = null,
      )
  }

  fun closeAddItemDialog() {
    releaseDragPermission?.invoke()
    releaseDragPermission = null
    state =
      state.copy(
        addItemDialogVisible = false,
        addItemDialogValidationMessage = null,
        addItemDialogPrefill = null,
      )
  }

  fun confirmAddItem(
    topicId: String,
    title: String,
    contentType: ContentType,
    sourceUrl: String?,
    summary: String,
    useAiSummary: Boolean,
    documentFormat: DocumentFormat? = null,
    fileName: String? = null,
  ) {
    val normalizedTitle = title.trim()
    val normalizedSummary = summary.trim()
    val normalizedSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() }

    val resolvedTopicId = resolveTopicId(topicId)
    val validationMessage =
      when {
        state.topics.none { it.id == resolvedTopicId } -> "请选择归属主题"
        normalizedTitle.isBlank() -> "请输入资料标题"
        contentType == ContentType.WEB_ARTICLE && normalizedSourceUrl == null -> "请输入链接"
        (contentType == ContentType.IMAGE_SCREENSHOT || contentType == ContentType.DOCUMENT) &&
          normalizedSourceUrl == null -> "请选择文件"
        else -> null
      }

    if (validationMessage != null) {
      state = state.copy(addItemDialogValidationMessage = validationMessage)
      return
    }

    val itemIndex = nextItemIndex++
    val now = Clock.System.now().toEpochMilliseconds()
    val finalSummary = if (useAiSummary) "" else normalizedSummary
    val item =
      KnowledgeItem(
        id = "item-user-$itemIndex",
        topicId = resolvedTopicId,
        contentType = contentType,
        title = normalizedTitle,
        summary = finalSummary,
        fullText = finalSummary,
        sourceUrl = normalizedSourceUrl,
        documentFormat = documentFormat,
        fileName = fileName,
        createdAtEpochMillis = now,
      )
    state =
      state.copy(
        items = state.items + item,
        topics =
          state.topics.map { topic ->
            if (topic.id == resolvedTopicId) topic.copy(updatedAtEpochMillis = now) else topic
          },
        selectedPane = AppPane.DETAIL,
        selectedTopicId = resolvedTopicId,
        activeDetailFilter = ContentType.ALL,
        addItemDialogVisible = false,
        addItemDialogValidationMessage = null,
        addItemDialogPrefill = null,
      )
    releaseDragPermission?.invoke()
    releaseDragPermission = null
    saveData()
  }

  fun confirmDeleteTopic() {
    val topicId = state.deleteConfirmTopicId ?: return
    closeDeleteConfirmDialog()
    deleteTopic(topicId)
  }

  fun deleteItem(itemId: String) {
    val deletingModalItem = state.modalItem?.id == itemId
    state =
      state.copy(
        items = state.items.filterNot { it.id == itemId },
        modalItem = if (deletingModalItem) null else state.modalItem,
        selectedPane =
          if (deletingModalItem && state.selectedPane == AppPane.CARD_DETAIL)
            state.selectedPane.let {
              if (state.selectedTopicId != null) AppPane.DETAIL else AppPane.TOPICS
            }
          else state.selectedPane,
      )
    saveData()
  }

  fun openDeleteItemConfirmDialog(itemId: String) {
    state = state.copy(deleteConfirmItemId = itemId)
  }

  fun closeDeleteItemConfirmDialog() {
    state = state.copy(deleteConfirmItemId = null)
  }

  fun confirmDeleteItem() {
    val itemId = state.deleteConfirmItemId ?: return
    closeDeleteItemConfirmDialog()
    deleteItem(itemId)
  }

  fun openEditItemDialog(itemId: String) {
    val item = state.items.firstOrNull { it.id == itemId } ?: return
    state = state.copy(editingItem = item, editItemDialogValidationMessage = null)
  }

  fun closeEditItemDialog() {
    state = state.copy(editingItem = null, editItemDialogValidationMessage = null)
  }

  fun confirmEditItem(
    title: String,
    contentType: ContentType,
    sourceUrl: String?,
    summary: String,
    useAiSummary: Boolean,
    documentFormat: DocumentFormat? = null,
    fileName: String? = null,
  ) {
    val originalItem = state.editingItem ?: return
    val normalizedTitle = title.trim()
    val normalizedSummary = summary.trim()
    val normalizedSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() }

    val validationMessage =
      when {
        normalizedTitle.isBlank() -> "请输入资料标题"
        else -> null
      }

    if (validationMessage != null) {
      state = state.copy(editItemDialogValidationMessage = validationMessage)
      return
    }

    val finalSummary = if (useAiSummary) "" else normalizedSummary
    val resolvedTopicId = resolveTopicId(originalItem.topicId)
    val updatedItem =
      originalItem.copy(
        topicId = resolvedTopicId,
        contentType = contentType,
        title = normalizedTitle,
        summary = finalSummary,
        fullText = finalSummary,
        sourceUrl = normalizedSourceUrl,
        documentFormat = documentFormat,
        fileName = fileName,
      )
    val now = Clock.System.now().toEpochMilliseconds()
    state =
      state.copy(
        items = state.items.map { if (it.id == originalItem.id) updatedItem else it },
        topics =
          state.topics.map { topic ->
            if (topic.id == resolvedTopicId) topic.copy(updatedAtEpochMillis = now) else topic
          },
        editingItem = null,
        editItemDialogValidationMessage = null,
        modalItem = if (state.modalItem?.id == originalItem.id) updatedItem else state.modalItem,
      )
    saveData()
  }

  fun openTopic(topicId: String) {
    val resolvedTopicId = resolveTopicId(topicId)
    if (state.topics.none { it.id == resolvedTopicId }) return

    state =
      state.copy(
        selectedPane = AppPane.DETAIL,
        selectedTopicId = resolvedTopicId,
        activeDetailFilter = ContentType.ALL,
        modalItem = null,
      )
  }

  fun updateParserInput(input: String) {
    state =
      state.copy(
        parserInput = input,
        parserValidationMessage = null,
        smartSummarizationMessage = null,
      )
  }

  fun updateHomeSearchQuery(query: String) {
    state = state.copy(homeSearchQuery = query)
  }

  fun submitParserInput() {
    summarizeParserInput(state.parserInput, closeClipboardOnSuccess = false)
  }

  private fun summarizeParserInput(rawInput: String, closeClipboardOnSuccess: Boolean) {
    if (state.isSmartSummarizing) return

    val normalizedInput = rawInput.trim()
    if (normalizedInput.isBlank()) {
      state =
        state.copy(
          parserValidationMessage = "请输入要归档的内容",
          smartSummarizationMessage = "请输入要智能总结的内容",
        )
      return
    }

    val currentLocalModelState = localModelStateProvider?.invoke() ?: state.localModelState
    if (
      state.aiSettings.engineType == AiEngineType.LOCAL_MODEL &&
        currentLocalModelState.status == LocalModelStatus.INFERENCING
    ) {
      state = state.copy(parserValidationMessage = "推理进行中", smartSummarizationMessage = "推理进行中")
      return
    }
    if (
      state.aiSettings.engineType == AiEngineType.LOCAL_MODEL &&
        currentLocalModelState.status != LocalModelStatus.READY
    ) {
      state =
        state.copy(
          parserValidationMessage = "本地模型未就绪，请先开启模型",
          smartSummarizationMessage = "本地模型未就绪，请先开启模型",
        )
      return
    }

    scope.launch {
      summarizeAndSave(rawInput, closeClipboardOnSuccess)
    }
  }

  private suspend fun summarizeAndSave(rawInput: String, closeClipboardOnSuccess: Boolean) {
    state =
      state.copy(
        isSmartSummarizing = true,
        parserValidationMessage = null,
        smartSummarizationMessage = null,
      )
    try {
      val result = summarizeRawInput(rawInput)
      handleSmartSummarizeResult(result, rawInput, closeClipboardOnSuccess)
    } catch (_: Exception) {
      state = state.copy(smartSummarizationMessage = "保存失败，请稍后重试")
    } finally {
      state = state.copy(isSmartSummarizing = false)
    }
  }

  private suspend fun summarizeRawInput(rawInput: String): SmartSummarizeResult {
    val request =
      createSmartSummarizeRequest(rawInput).getOrElse { reason ->
        val prefix = if (reason is DocumentExtractionException) "文档内容解析失败" else "网页内容获取失败"
        return SmartSummarizeResult.Failure("$prefix：${reason.message.orEmpty()}")
      }

    if (state.aiSettings.engineType != AiEngineType.LOCAL_MODEL) {
      val summarizer = smartSummarizer ?: remoteSmartSummarizerFactory(state.aiSettings)
      return summarizer.summarize(
        request,
        state.topics,
        state.items,
      )
    }

    val directEngine = localLlmEngine
    state =
      state.copy(
        localModelState = state.localModelState.copy(status = LocalModelStatus.INFERENCING)
      )
    return try {
      if (directEngine != null) {
        LocalLlmSmartSummarizer(directEngine).summarize(request, state.topics, state.items)
      } else {
        inferenceConnection?.summarize(request, state.topics, state.items)
          ?: SmartSummarizeResult.Failure(LOCAL_AI_UNAVAILABLE_MESSAGE)
      }
    } finally {
      if (state.localModelState.status == LocalModelStatus.INFERENCING) {
        state =
          state.copy(localModelState = state.localModelState.copy(status = LocalModelStatus.READY))
      }
    }
  }

  private suspend fun createSmartSummarizeRequest(rawInput: String): Result<SmartSummarizeRequest> {
    documentSummarizeSource(rawInput)?.let { source ->
      return createDocumentSmartSummarizeRequest(rawInput, source)
    }

    val detected =
      WebUrlDetector.detect(rawInput)?.takeIf { it.isBare }
        ?: return Result.success(SmartSummarizeRequest(rawText = rawInput))

    return when (
      val fetched = webPageContentFetcher.fetch(detected.originalUrl, detected.fetchUrl)
    ) {
      is WebPageContentFetchResult.Failure -> Result.failure(WebPageFetchException(fetched.message))
      is WebPageContentFetchResult.Success -> {
        val content = fetched.content
        Result.success(
          SmartSummarizeRequest(
            rawText = rawInput,
            sourceUrl = detected.originalUrl,
            sourceTitle = content.title,
            fetchedWebContext =
              FetchedWebContext(
                originalUrl = detected.originalUrl,
                title = content.title,
                description = content.description,
                bodyText = content.bodyText,
              ),
          )
        )
      }
    }
  }

  private suspend fun createDocumentSmartSummarizeRequest(
    rawInput: String,
    source: DocumentSummarizeSource,
  ): Result<SmartSummarizeRequest> {
    val extractor =
      documentContentExtractor ?: return Result.failure(DocumentExtractionException("文档解析不可用"))
    return when (val extracted = extractor.extract(source.source, source.format, source.fileName)) {
      is DocumentContentExtractionResult.Failure ->
        Result.failure(DocumentExtractionException(extracted.message))
      is DocumentContentExtractionResult.Success -> {
        val content = extracted.content
        Result.success(
          SmartSummarizeRequest(
            rawText = rawInput,
            sourceUrl = source.source.sourceKey,
            sourceTitle = content.fileName,
            fetchedDocumentContext =
              FetchedDocumentContext(
                fileName = content.fileName,
                format = content.format,
                extractedText = content.extractedText,
                originalCharCount = content.originalCharCount,
                isTruncated = content.isTruncated,
              ),
          )
        )
      }
    }
  }

  private suspend fun handleSmartSummarizeResult(
    result: SmartSummarizeResult,
    rawInput: String,
    closeClipboardOnSuccess: Boolean,
  ) {
    when (result) {
      is SmartSummarizeResult.Failure -> {
        state = state.copy(smartSummarizationMessage = result.message)
      }

      is SmartSummarizeResult.Success ->
        saveSmartSummarizeSuccess(result, rawInput, closeClipboardOnSuccess)
    }
  }

  private suspend fun saveSmartSummarizeSuccess(
    result: SmartSummarizeResult.Success,
    rawInput: String,
    closeClipboardOnSuccess: Boolean,
  ) {
    val resolvedTopicId = resolveTopicId(result.topicId)
    val validationMessage =
      when {
        state.topics.none { it.id == resolvedTopicId } -> "智能总结结果无效，请重试"
        result.contentType == ContentType.ALL -> "智能总结结果无效，请重试"
        result.title.isBlank() -> "智能总结结果无效，请重试"
        else -> null
      }
    if (validationMessage != null) {
      state = state.copy(smartSummarizationMessage = validationMessage)
      return
    }

    val itemIndex = nextItemIndex
    val now = Clock.System.now().toEpochMilliseconds()
    val isPlainTextInput = !rawInput.isBareWebUrl() && !isDocumentOnlyClipboardInput(rawInput)
    val storedContentType =
      if (isPlainTextInput && result.contentType == ContentType.WEB_ARTICLE) {
        ContentType.DOCUMENT
      } else {
        result.contentType
      }
    val storedDocumentFormat =
      if (storedContentType == ContentType.DOCUMENT && isPlainTextInput) {
        DocumentFormat.MARKDOWN
      } else {
        result.documentFormat
      }
    val generatedMarkdownPath =
      if (storedContentType == ContentType.DOCUMENT && isPlainTextInput) {
        writeSmartMarkdownDocument(result.title, rawInput)
      } else {
        null
      }
    val sourceUrl =
      generatedMarkdownPath
        ?: result.sourceUrl.takeIf { storedContentType == ContentType.WEB_ARTICLE }
        ?: rawInput.extractSourceUrl(storedContentType)
    val item =
      KnowledgeItem(
        id = "item-classified-$itemIndex",
        topicId = resolvedTopicId,
        contentType = storedContentType,
        title = result.title,
        summary = result.summary,
        fullText = if (generatedMarkdownPath != null) "" else rawInput,
        sourceUrl = sourceUrl,
        documentFormat = storedDocumentFormat,
        fileName = generatedMarkdownPath?.substringAfterLast('/'),
        createdAtEpochMillis = now,
      )
    val topics =
      state.topics.map { topic ->
        if (topic.id == resolvedTopicId) topic.copy(updatedAtEpochMillis = now) else topic
      }
    val items = state.items + item

    persistData(topics, items)
    nextItemIndex++
    state =
      state.copy(
        items = items,
        topics = topics,
        parserInput = "",
        parserValidationMessage = null,
        smartSummarizationMessage = null,
        selectedPane = AppPane.DETAIL,
        selectedTopicId = resolvedTopicId,
        activeDetailFilter = ContentType.ALL,
        clipboardContent = if (closeClipboardOnSuccess) null else state.clipboardContent,
        clipboardImageUri = if (closeClipboardOnSuccess) null else state.clipboardImageUri,
        clipboardSourceUri = if (closeClipboardOnSuccess) null else state.clipboardSourceUri,
        clipboardSourceContentType =
          if (closeClipboardOnSuccess) null else state.clipboardSourceContentType,
        clipboardSourceDocumentFormat =
          if (closeClipboardOnSuccess) null else state.clipboardSourceDocumentFormat,
        clipboardSourceFileName =
          if (closeClipboardOnSuccess) null else state.clipboardSourceFileName,
        clipboardSourceLabel = if (closeClipboardOnSuccess) null else state.clipboardSourceLabel,
        ignoredClipboardSnapshot =
          if (closeClipboardOnSuccess) null else state.ignoredClipboardSnapshot,
        showClipboardDialog = if (closeClipboardOnSuccess) false else state.showClipboardDialog,
      )
    if (closeClipboardOnSuccess) {
      releaseDragPermission?.invoke()
      releaseDragPermission = null
    }
  }

  private fun handleParserClassificationResult(result: ClassificationResult) {
    when (result) {
      is ClassificationResult.BlankInput -> {
        state = state.copy(parserValidationMessage = result.message)
      }

      is ClassificationResult.Unknown -> {
        state = state.copy(parserValidationMessage = "分类失败，请输入有效内容")
      }

      is ClassificationResult.Classified -> {
        val payload = result.payload
        val resolvedTopicId = resolveTopicId(payload.topicId)
        val itemIndex = nextItemIndex++
        val now = Clock.System.now().toEpochMilliseconds()
        val item =
          KnowledgeItem(
            id = "item-classified-$itemIndex",
            topicId = resolvedTopicId,
            contentType = payload.contentType,
            title = payload.title,
            summary = payload.summary,
            fullText = payload.rawInput,
            sourceUrl = payload.rawInput.extractSourceUrl(payload.contentType),
            documentFormat = payload.documentFormat,
            createdAtEpochMillis = now,
          )
        state =
          state.copy(
            items = state.items + item,
            topics =
              state.topics.map { topic ->
                if (topic.id == resolvedTopicId) topic.copy(updatedAtEpochMillis = now) else topic
              },
            parserInput = "",
            parserValidationMessage = null,
            selectedPane = AppPane.DETAIL,
            selectedTopicId = resolvedTopicId,
            activeDetailFilter = ContentType.ALL,
          )
        saveData()
      }
    }
  }

  private suspend fun classifyParserInput(): ClassificationResult {
    val engine =
      localLlmEngine ?: inferenceConnection?.getEngine() ?: return ClassificationResult.Unknown
    state =
      state.copy(
        localModelState = state.localModelState.copy(status = LocalModelStatus.INFERENCING)
      )
    return try {
      LocalLlmClassifier(engine).classify(state.parserInput, state.topics)
    } finally {
      if (state.localModelState.status == LocalModelStatus.INFERENCING) {
        state =
          state.copy(localModelState = state.localModelState.copy(status = LocalModelStatus.READY))
      }
    }
  }

  fun createTopic(title: String) {
    rejectTopicCrud()
  }

  fun renameTopic(topicId: String, title: String) {
    rejectTopicCrud()
  }

  fun deleteTopic(topicId: String) {
    rejectTopicCrud()
  }

  private fun rejectTopicCrud() {
    state = state.copy(topicValidationMessage = TOPIC_CRUD_DISABLED_MESSAGE)
  }

  fun selectFilter(contentType: ContentType) {
    state = state.copy(activeDetailFilter = contentType)
  }

  fun openCardModal(itemId: String) {
    val item = state.items.firstOrNull { it.id == itemId } ?: return
    state = state.copy(selectedPane = AppPane.CARD_DETAIL, modalItem = item)
  }

  fun closeCardModal() {
    state =
      state.copy(
        selectedPane = if (state.selectedTopicId == null) AppPane.TOPICS else AppPane.DETAIL,
        modalItem = null,
      )
  }

  fun showClipboard(
    content: String,
    imageUri: String? = null,
    sourceUri: String? = imageUri,
    sourceContentType: ContentType? = if (imageUri != null) ContentType.IMAGE_SCREENSHOT else null,
    sourceDocumentFormat: DocumentFormat? = null,
    sourceFileName: String? = null,
    sourceLabel: String? = null,
  ) {
    val normalizedContent = content.trim().takeIf { it.isNotBlank() }
    val normalizedImageUri = imageUri?.trim()?.takeIf { it.isNotBlank() }
    val normalizedSourceUri = sourceUri?.trim()?.takeIf { it.isNotBlank() }
    val normalizedFileName = sourceFileName?.trim()?.takeIf { it.isNotBlank() }
    if (normalizedContent == null && normalizedImageUri == null && normalizedSourceUri == null)
      return

    val snapshot =
      ClipboardSnapshot(
        content = normalizedContent,
        imageUri = normalizedImageUri,
        sourceUri = normalizedSourceUri,
        sourceContentType = sourceContentType,
        sourceDocumentFormat = sourceDocumentFormat,
        sourceFileName = normalizedFileName,
        sourceLabel = sourceLabel,
      )
    if (snapshot == state.ignoredClipboardSnapshot) {
      state = state.copy(latestClipboardSnapshot = snapshot)
      return
    }

    state =
      state.copy(
        clipboardContent = normalizedContent,
        clipboardImageUri = normalizedImageUri,
        clipboardSourceUri = normalizedSourceUri,
        clipboardSourceContentType = sourceContentType,
        clipboardSourceDocumentFormat = sourceDocumentFormat,
        clipboardSourceFileName = normalizedFileName,
        clipboardSourceLabel = sourceLabel,
        latestClipboardSnapshot = snapshot,
        showClipboardDialog = true,
      )
  }

  fun dismissClipboardDialog() {
    releaseDragPermission?.invoke()
    releaseDragPermission = null
    val ignoredSnapshot = currentClipboardSnapshot()
    state =
      state.copy(
        clipboardContent = null,
        clipboardImageUri = null,
        clipboardSourceUri = null,
        clipboardSourceContentType = null,
        clipboardSourceDocumentFormat = null,
        clipboardSourceFileName = null,
        clipboardSourceLabel = null,
        ignoredClipboardSnapshot = ignoredSnapshot ?: state.ignoredClipboardSnapshot,
        showClipboardDialog = false,
      )
  }

  fun openLatestClipboardDialog() {
    val snapshot = state.latestClipboardSnapshot ?: return
    state =
      state.copy(
        clipboardContent = snapshot.content,
        clipboardImageUri = snapshot.imageUri,
        clipboardSourceUri = snapshot.sourceUri,
        clipboardSourceContentType = snapshot.sourceContentType,
        clipboardSourceDocumentFormat = snapshot.sourceDocumentFormat,
        clipboardSourceFileName = snapshot.sourceFileName,
        clipboardSourceLabel = snapshot.sourceLabel,
        showClipboardDialog = true,
      )
  }

  fun acceptClipboardAndSummarize() {
    val content =
      state.clipboardContent ?: state.clipboardSourceFileName ?: state.clipboardSourceUri ?: return
    state = state.copy(parserInput = content)
    summarizeParserInput(content, closeClipboardOnSuccess = true)
  }

  fun acceptClipboardAndManualCreate() {
    val targetTopicId =
      state.selectedTopicId?.let(::resolveTopicId)?.takeIf { selectedTopicId ->
        state.topics.any { it.id == selectedTopicId }
      } ?: state.recentTopics.firstOrNull()?.id ?: return
    val prefill = clipboardAddItemPrefill()
    state =
      state.copy(
        clipboardContent = null,
        clipboardImageUri = null,
        clipboardSourceUri = null,
        clipboardSourceContentType = null,
        clipboardSourceDocumentFormat = null,
        clipboardSourceFileName = null,
        clipboardSourceLabel = null,
        ignoredClipboardSnapshot = null,
        showClipboardDialog = false,
        selectedPane = AppPane.DETAIL,
        selectedTopicId = targetTopicId,
        activeDetailFilter = ContentType.ALL,
        modalItem = null,
        addItemDialogVisible = true,
        addItemDialogValidationMessage = null,
        addItemDialogPrefill = prefill,
      )
  }

  fun updateAiSettings(settings: AiEngineSettings) {
    val previous = state.aiSettings
    state = state.copy(aiSettings = settings)
    saveAiSettings()
    if (
      previous.engineType == AiEngineType.LOCAL_MODEL &&
        settings.engineType != AiEngineType.LOCAL_MODEL
    ) {
      when (state.localModelState.status) {
        LocalModelStatus.READY,
        LocalModelStatus.INITIALIZING -> stopModel()
        LocalModelStatus.DOWNLOADING -> cancelDownload()
        else -> Unit
      }
    }
  }

  fun downloadModel() {
    if (state.localModelState.status == LocalModelStatus.DOWNLOADING) return
    val manager = modelDownloadManager ?: return
    scope.launch {
      manager.startDownload(GEMMA_4_E4B_IT)
    }
  }

  fun cancelDownload() {
    val manager = modelDownloadManager ?: return
    scope.launch {
      manager.cancelDownload()
    }
  }

  fun importLocalModel(source: ContentSource) {
    if (
      state.localModelState.status in
        setOf(LocalModelStatus.DOWNLOADING, LocalModelStatus.INITIALIZING)
    )
      return
    val manager = modelDownloadManager ?: return
    scope.launch {
      manager.importModel(GEMMA_4_E4B_IT, source)
    }
  }

  fun startModel() {
    if (
      state.localModelState.status in setOf(LocalModelStatus.INITIALIZING, LocalModelStatus.READY)
    )
      return
    // The model-file existence check now touches platform storage, so it runs inside a coroutine.
    scope.launch {
      if (shouldValidateLocalModelFileBeforeStart() && !isLocalModelFilePresent()) {
        resetLocalModelState()
        state =
          state.copy(
            localModelState =
              LocalModelState(
                status = LocalModelStatus.ERROR,
                errorMessage = "模型文件不存在，请重新下载",
              )
          )
        return@launch
      }
      val connection = inferenceConnection ?: return@launch
      connection.bind()
      connection.startModel(GEMMA_4_E4B_IT, state.aiSettings.localBackendPreference)
      if (
        state.localModelState.status !in
          setOf(LocalModelStatus.INITIALIZING, LocalModelStatus.READY)
      ) {
        state =
          state.copy(
            localModelState = state.localModelState.copy(status = LocalModelStatus.INITIALIZING)
          )
      }
    }
  }

  fun stopModel() {
    if (state.localModelState.status == LocalModelStatus.STOPPING) return
    inferenceConnection?.stopModel()
  }

  fun updateBackendPreference(backend: InferenceBackend) {
    updateAiSettings(state.aiSettings.copy(localBackendPreference = backend))
  }

  fun runBenchmark() {
    if (state.localModelState.status != LocalModelStatus.READY || state.isBenchmarkRunning) return
    val engine = inferenceConnection?.getEngine() ?: localLlmEngine ?: return
    state = state.copy(isBenchmarkRunning = true, benchmarkResult = null)
    scope.launch {
      val result = engine.benchmark(128, 128)
      state =
        state.copy(
          benchmarkResult = result.getOrNull(),
          isBenchmarkRunning = false,
        )
    }
  }

  private fun topicTitleValidationMessage(title: String, currentTopicId: String? = null): String? =
    when {
      title.isBlank() -> "请输入主题名称"
      state.topics.any { it.id != currentTopicId && it.title == title } -> "主题名称已存在"
      else -> null
    }

  private fun String.extractSourceUrl(contentType: ContentType): String? {
    if (contentType != ContentType.WEB_ARTICLE) return null
    return lineSequence()
      .flatMap { it.splitToSequence(' ', '\t', '\n') }
      .firstOrNull {
        it.startsWith("http://") || it.startsWith("https://") || it.startsWith("www.")
      }
  }

  private fun documentSummarizeSource(rawInput: String): DocumentSummarizeSource? {
    if (!isDocumentOnlyClipboardInput(rawInput)) return null
    val sourceUri =
      state.clipboardSourceUri?.let(::resolveContentSource) ?: return null
    return DocumentSummarizeSource(
      source = sourceUri,
      format = state.clipboardSourceDocumentFormat ?: DocumentFormat.UNKNOWN,
      fileName = state.clipboardSourceFileName,
    )
  }

  private fun isDocumentOnlyClipboardInput(rawInput: String): Boolean {
    if (state.clipboardSourceContentType != ContentType.DOCUMENT) return false
    val sourceUri = state.clipboardSourceUri ?: return false
    return state.clipboardContent.isNullOrBlank() &&
      (rawInput == state.clipboardSourceFileName || rawInput == sourceUri)
  }

  private fun writeSmartMarkdownDocument(title: String, content: String): String? {
    val itemsDir = itemsDirProvider?.invoke() ?: return null
    return writeMarkdownFile(itemsDir, title, content)
  }

  private fun String.isBareWebUrl(): Boolean {
    val trimmed = trim()
    return trimmed.isNotBlank() &&
      !trimmed.any(Char::isWhitespace) &&
      (trimmed.startsWith("http://") ||
        trimmed.startsWith("https://") ||
        trimmed.startsWith("www."))
  }

  private fun localModelPath(): String = "${fileStore.modelsDir}/${GEMMA_4_E4B_IT.fileName}"

  private suspend fun isLocalModelFilePresent(): Boolean =
    localModelFileExists?.invoke() ?: fileStore.exists(localModelPath())

  private fun resetLocalModelState() {
    state = state.copy(localModelState = LocalModelState(status = LocalModelStatus.NOT_DOWNLOADED))
  }

  private fun shouldValidateLocalModelFileBeforeStart(): Boolean =
    state.localModelState.status in setOf(LocalModelStatus.DOWNLOADED, LocalModelStatus.READY) &&
      state.localModelState.modelPath != null

  private fun currentClipboardSnapshot(): ClipboardSnapshot? {
    return ClipboardSnapshot(
        content = state.clipboardContent,
        imageUri = state.clipboardImageUri,
        sourceUri = state.clipboardSourceUri,
        sourceContentType = state.clipboardSourceContentType,
        sourceDocumentFormat = state.clipboardSourceDocumentFormat,
        sourceFileName = state.clipboardSourceFileName,
        sourceLabel = state.clipboardSourceLabel,
      )
      .takeIf { it.content != null || it.imageUri != null || it.sourceUri != null }
  }

  private fun clipboardAddItemPrefill(): AddItemDialogPrefill {
    val content = state.clipboardContent.orEmpty()
    val sourceUri = state.clipboardSourceUri
    val sourceContentType = state.clipboardSourceContentType
    if (sourceUri != null && sourceContentType != null) {
      val isMixedImageText =
        content.isNotBlank() && sourceContentType == ContentType.IMAGE_SCREENSHOT
      return AddItemDialogPrefill(
        title = content.ifBlank { state.clipboardSourceFileName.orEmpty() },
        contentType = if (isMixedImageText) ContentType.DOCUMENT else sourceContentType,
        sourceUrl = if (isMixedImageText) null else sourceUri,
        documentFormat =
          if (isMixedImageText) DocumentFormat.MARKDOWN else state.clipboardSourceDocumentFormat,
        fileName = state.clipboardSourceFileName,
        lockContentType = true,
        availableContentTypes = if (isMixedImageText) listOf(ContentType.DOCUMENT) else null,
      )
    }

    val sourceUrl = content.takeIf { it.isBareWebUrl() }
    return AddItemDialogPrefill(
      contentType = if (sourceUrl != null) ContentType.WEB_ARTICLE else ContentType.DOCUMENT,
      sourceUrl = sourceUrl,
      documentFormat = if (sourceUrl == null) DocumentFormat.MARKDOWN else null,
      lockContentType = sourceUrl == null,
      availableContentTypes =
        if (sourceUrl != null) {
          listOf(ContentType.WEB_ARTICLE, ContentType.DOCUMENT)
        } else {
          listOf(ContentType.DOCUMENT)
        },
      textContent = content,
    )
  }

  private companion object {
    const val TAG = "ArchiveAssistantStateStore"
    const val TOPIC_CRUD_DISABLED_MESSAGE = "六部分类已固定，不能新建、重命名或删除。"
    const val SMART_SUMMARIZER_UNAVAILABLE_MESSAGE = "智能总结不可用，请先配置真实 AI 引擎"
    const val LOCAL_AI_UNAVAILABLE_MESSAGE = "本地 AI 不可用，请先开启模型"
  }

  private class WebPageFetchException(message: String) : Exception(message)

  private class DocumentExtractionException(message: String) : Exception(message)

  private data class DocumentSummarizeSource(
    val source: ContentSource,
    val format: DocumentFormat,
    val fileName: String?,
  )
}
