package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import com.lyihub.archiveassistant.platform.toByteArray
import com.lyihub.archiveassistant.platform.toNSData
import platform.Foundation.NSData
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"
  return if (content.encodeToByteArray().toNSData().writeToFile(path, atomically = true)) path
  else null
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
