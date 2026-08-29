package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.Buffer
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSData
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL

/**
 * Copies an [NSData] payload into a Kotlin [ByteArray].
 *
 * Uses `getBytes` into a pinned buffer rather than indexing the raw `bytes` pointer: indexing binds
 * to the Regex `get` operator on some targets and yields a confusing MatchGroup type error.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun copyOut(data: NSData): ByteArray {
  val size = data.length.toInt()
  if (size == 0) return ByteArray(0)
  val out = ByteArray(size)
  out.usePinned { pinned -> data.getBytes(pinned.addressOf(0), length = size.toULong()) }
  return out
}

/** Reads a bundled resource into a [ByteArray]. */
internal fun readUrlBytes(url: NSURL): ByteArray? {
  val data = NSData.dataWithContentsOfURL(url) ?: return null
  return copyOut(data)
}

/** Reads a file into a [ByteArray] using kotlinx-io. */
internal fun readFileBytes(path: String): ByteArray? =
  runCatching {
      val buffer = Buffer()
      SystemFileSystem.source(Path(path)).use { source -> source.transferTo(buffer) }
      buffer.readByteArray()
    }
    .getOrNull()

/** Writes [bytes] to [path] using kotlinx-io. */
internal fun writeFileBytes(path: String, bytes: ByteArray) {
  val buffer = Buffer()
  buffer.write(bytes, 0, bytes.size)
  SystemFileSystem.sink(Path(path)).use { sink -> buffer.transferTo(sink) }
}

actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"
  return runCatching {
      writeFileBytes(path, content.encodeToByteArray())
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
