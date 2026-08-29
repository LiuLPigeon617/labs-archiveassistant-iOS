package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"

  val bytes = content.encodeToByteArray()
  if (bytes.isEmpty()) {
    return if (NSData.data().writeToFile(path, atomically = true)) path else null
  }
  val data = bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
  return if (data.writeToFile(path, atomically = true)) path else null
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

  @OptIn(ExperimentalForeignApi::class)
  override suspend fun openRead(): ByteArray? {
    val data = NSData.dataWithContentsOfFile(sourceKey) ?: return null
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> data.getBytes(pinned.addressOf(0), length = size.toULong()) }
    return result
  }
}
