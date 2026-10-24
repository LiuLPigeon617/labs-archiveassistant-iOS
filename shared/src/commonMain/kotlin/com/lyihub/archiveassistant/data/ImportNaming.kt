package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.PlatformFileStore

/**
 * Filename rules shared by every platform.
 *
 * The Android build kept these inside `app/src/main/java/com/lyihub/archiveassistant/data/UriImportHelper.kt`,
 * mixed in with `Context`/`File` plumbing. Only the naming half belongs in `commonMain`: it is pure
 * string work, and it is the half that has to agree with the files already sitting in `itemsDir`.
 *
 * Uniqueness needs a directory probe, so [uniqueImportPath] takes a [PlatformFileStore] instead of
 * reading the `platformFileStore()` global. A test can then supply an in-memory fake and no platform
 * has to be present, and the function stays testable on the one target this project can run locally.
 */

/**
 * Picks the on-disk name for an imported file.
 *
 * A content provider's display name may carry a full path, a NUL byte, or nothing at all, so the
 * value is stripped down to a single safe last segment before it is ever used as a path component.
 */
internal fun importFileName(displayName: String?, fallbackExtension: String): String {
  val sanitizedName =
    displayName
      ?.substringAfterLast('/')
      ?.substringAfterLast('\\')
      ?.replace('\u0000', '_')
      ?.trim()
      ?.takeIf { it.isNotBlank() }

  return sanitizedName ?: "imported-file$fallbackExtension"
}

/**
 * Derives a Markdown filename from an item title.
 *
 * [String.lines] rather than [String.lineSequence]: the latter is a JVM-only stdlib extension, so the
 * Android original compiled fine on desktop and would fail on Kotlin/Native with
 * `unresolved reference: lineSequence`. That is the exact failure mode this port has to avoid.
 */
internal fun markdownFileName(title: String): String {
  val baseName =
    title
      .lines()
      .firstOrNull()
      .orEmpty()
      .trim()
      .take(48)
      .replace(Regex("[\\\\/:*?\"<>|]+"), "-")
      .ifBlank { "clipboard-note" }
  return if (baseName.endsWith(".md", ignoreCase = true)) baseName else "$baseName.md"
}

/**
 * Resolves [fileName] inside [directory], appending ` (1)`, ` (2)`, … until the path is free.
 *
 * Same collision scheme as Android's `uniqueImportFile`, so a file imported on one platform and then
 * imported again on another does not silently overwrite the first one.
 */
internal suspend fun uniqueImportPath(
  fileStore: PlatformFileStore,
  directory: String,
  fileName: String,
): String {
  val initialPath = pathIn(directory, fileName)
  if (!fileStore.exists(initialPath)) return initialPath

  val dotIndex = fileName.lastIndexOf('.').takeIf { it > 0 }
  val baseName = dotIndex?.let { fileName.substring(0, it) } ?: fileName
  val extension = dotIndex?.let { fileName.substring(it) } ?: ""
  var suffix = 1

  while (true) {
    val candidate = pathIn(directory, "$baseName ($suffix)$extension")
    if (!fileStore.exists(candidate)) return candidate
    suffix += 1
  }
}

/** Joins a directory and a file name, tolerating a trailing separator on the directory. */
internal fun pathIn(directory: String, fileName: String): String =
  "${directory.trimEnd('/', '\\')}/$fileName"
