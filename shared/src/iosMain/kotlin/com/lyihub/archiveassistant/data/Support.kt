package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"

  val nsString = content as NSString
  val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return null
  val written = data.writeToFile(path, atomically = true)
  return if (written) path else null
}

/**
 * iOS content sources are file paths inside the app's private storage.
 *
 * Files coming from the Files app or a share sheet arrive as security-scoped URLs; the platform
 * layer is responsible for turning those into a readable path before this is constructed.
 */
class IosContentSource(override val sourceKey: String) : ContentSource {
  override val displayName: String?
    get() = sourceKey.substringAfterLast('/').takeIf { it.isNotBlank() }

  override suspend fun openRead(): ByteArray? {
    val data = NSData.dataWithContentsOfFile(sourceKey) ?: return null
    val size = data.length.toInt()
    if (size == 0) return ByteArray(0)
    val result = ByteArray(size)
    result.usePinned { pinned -> data.getBytes(pinned.addressOf(0), length = size.toULong()) }
    return result
  }
}
