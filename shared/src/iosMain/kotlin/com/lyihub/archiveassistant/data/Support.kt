package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

/**
 * Reads a bundled resource into a [ByteArray].
 *
 * `NSData.dataWithContentsOfURL` is a Foundation factory method present in the Kotlin/Native
 * bindings. Bytes are read via `bytes`/`length` instead of `getBytes`, which would require a pinned
 * buffer and an experimental-API opt-in on every call site.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun readUrlBytes(url: NSURL): ByteArray? {
  val data = NSData.dataWithContentsOfURL(url) ?: return null
  val size = data.length.toInt()
  if (size == 0) return ByteArray(0)
  val pointer = data.bytes ?: return null
  return ByteArray(size) { index -> pointer[index] }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  if (size == 0) return ByteArray(0)
  val pointer = bytes ?: return ByteArray(0)
  return ByteArray(size) { index -> pointer[index] }
}

actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"
  return runCatching {
      SystemFileSystem.sink(Path(path)).use { sink -> sink.write(content.encodeToByteArray()) }
      path
    }
    .getOrNull()
}

/**
 * iOS content sources are file paths inside the app's private storage.
 *
 * Files arriving from the Files app or a share sheet come as security-scoped URLs; the platform
 * layer turns those into a readable path before this is constructed.
 */
class IosContentSource(override val sourceKey: String) : ContentSource {
  override val displayName: String?
    get() = sourceKey.substringAfterLast('/').takeIf { it.isNotBlank() }

  override suspend fun openRead(): ByteArray? = NSData.dataWithContentsOfFile(sourceKey)?.toByteArray()
}
