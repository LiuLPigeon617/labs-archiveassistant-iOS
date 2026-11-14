package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import com.lyihub.archiveassistant.platform.PlatformFileStore
import com.lyihub.archiveassistant.platform.platformFileStore

/**
 * The other two DetailPane seams, and unlike the decoder and the picker these have no platform half
 * at all: both are file-store plumbing on top of the naming rules in [ImportNaming].
 *
 * They used to live in [com.lyihub.archiveassistant.data.UriImportHelper] as
 * `copyUriToFile(context, uri, destFile)` and `writeMarkdownPrefillFile(context, title, content)`.
 * The only Android-specific part of each was `Context`; the actual work was "read bytes, pick a free
 * name, write bytes", which is the same everywhere.
 */

/** Materialises an externally chosen file into app-private storage. */
interface ContentImporter {
  /**
   * Copies [source] into [directory] under a free, sanitised name.
   *
   * Returns the written path, or null when the source could not be read (a revoked content-provider
   * grant, a file removed between picking and importing).
   */
  suspend fun materialize(source: ContentSource, directory: String): String?
}

/**
 * [ContentImporter] over a [PlatformFileStore].
 *
 * Takes the store as a parameter rather than calling [platformFileStore] internally so a test can
 * pass an in-memory fake — the same reason [uniqueImportPath] does.
 */
class FileStoreContentImporter(private val fileStore: PlatformFileStore) : ContentImporter {

  override suspend fun materialize(source: ContentSource, directory: String): String? {
    val bytes = source.openRead() ?: return null
    val fileName = importFileName(source.displayName, source.extensionHint())
    val path = uniqueImportPath(fileStore, directory, fileName)
    fileStore.writeBytes(path, bytes)
    return path
  }
}

/** Builds the default importer, using the process-wide file store unless one is supplied. */
fun contentImporter(fileStore: PlatformFileStore = platformFileStore()): ContentImporter =
  FileStoreContentImporter(fileStore)

/**
 * Writes `content` to a Markdown file named after [title], inside [itemsDir].
 *
 * Same two rules as the Android original: the name comes from [markdownFileName] (first line, no
 * illegal characters, 48 characters, `.md`), and an existing file is never overwritten —
 * [uniqueImportPath] appends ` (1)`, ` (2)`, …
 *
 * This is not the same thing as [writeMarkdownFile]: that one predates the shared naming rules,
 * overwrites on collision, and is not `suspend`. New call sites should use this one.
 */
suspend fun writeMarkdownPrefill(
  fileStore: PlatformFileStore,
  itemsDir: String,
  title: String,
  content: String,
): String? {
  if (itemsDir.isBlank()) return null
  val path = uniqueImportPath(fileStore, itemsDir, markdownFileName(title))
  fileStore.writeBytes(path, content.encodeToByteArray())
  return path
}

/**
 * The extension a [ContentSource] should fall back to when its display name is unusable.
 *
 * Matches the Android call site, which passed `selectedFileName.substringAfterLast('.', "")` — no
 * leading dot — so [importFileName]'s `imported-file$extension` fallback keeps the same shape.
 */
private fun ContentSource.extensionHint(): String =
  displayName?.takeIf { it.contains('.') }?.substringAfterLast('.', "").orEmpty()
