package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.reinterpret
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

/**
 * Copies bytes out of an [NSData].
 *
 * `NSData.bytes` is typed `CPointer<out CPointed>?` and must be reinterpreted to `ByteVar` before
 * indexing. The target is bound to an explicit [CPointer] receiver because a bare `pointer[i]` can
 * otherwise bind to the Regex `get` operator.
 */
@OptIn(ExperimentalForeignApi::class)
private fun copyOut(data: NSData): ByteArray {
  val size = data.length.toInt()
  if (size == 0) return ByteArray(0)
  val pointer: CPointer<ByteVar>? = data.bytes?.reinterpret()
  if (pointer == null) return ByteArray(0)
  return ByteArray(size) { index -> pointer[index] }
}

/** Reads a bundled resource into a [ByteArray]. */
internal fun readUrlBytes(url: NSURL): ByteArray? {
  val data = NSData.dataWithContentsOfURL(url) ?: return null
  return copyOut(data)
}

actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"
  return runCatching {
      val bytes = content.encodeToByteArray()
      SystemFileSystem.sink(Path(path)).buffered().use { sink ->
        sink.write(bytes, 0, bytes.size)
        sink.flush()
      }
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

  override suspend fun openRead(): ByteArray? {
    val data = NSData.dataWithContentsOfFile(sourceKey) ?: return null
    return copyOut(data)
  }
}
