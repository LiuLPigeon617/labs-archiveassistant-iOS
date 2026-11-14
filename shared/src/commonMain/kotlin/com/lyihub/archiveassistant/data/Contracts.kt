package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.LocalModelInfo
import com.lyihub.archiveassistant.domain.LocalModelState
import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.coroutines.flow.Flow

/**
 * Downloads and imports on-device models.
 *
 * `importModel` takes a [ContentSource] rather than an Android `Uri`: iOS resolves
 * security-scoped bookmarks into readable bytes inside the platform implementation.
 */
interface ModelDownloadManager {
  val downloadState: Flow<LocalModelState>

  suspend fun startDownload(model: LocalModelInfo): Result<Unit>

  suspend fun cancelDownload()

  suspend fun deleteModel(model: LocalModelInfo): Result<Unit>

  suspend fun importModel(model: LocalModelInfo, source: ContentSource): Result<Unit>

  fun isModelPresent(model: LocalModelInfo): Boolean
}

sealed interface DocumentContentExtractionResult {
  data class Success(val content: ExtractedDocumentContent) : DocumentContentExtractionResult

  data class Failure(val message: String) : DocumentContentExtractionResult
}

data class ExtractedDocumentContent(
  val fileName: String,
  val format: DocumentFormat,
  val extractedText: String,
  val originalCharCount: Int,
  val isTruncated: Boolean,
)

/**
 * Extracts text from a document.
 *
 * The Android build carried a 238-line implementation bound to `Context` and `java.util.zip`. That
 * implementation is being re-platformed (PDFKit on iOS, PDFBox on Android, kotlinx-io for DOCX).
 * [NoOpDocumentContentExtractor] stands in so the shared state store compiles on every target.
 */
interface DocumentContentExtractor {
  suspend fun extract(
    source: ContentSource,
    format: DocumentFormat,
    fileName: String?,
  ): DocumentContentExtractionResult
}

object NoOpDocumentContentExtractor : DocumentContentExtractor {
  override suspend fun extract(
    source: ContentSource,
    format: DocumentFormat,
    fileName: String?,
  ): DocumentContentExtractionResult =
    DocumentContentExtractionResult.Failure("文档解析器尚未在当前平台接入")
}
