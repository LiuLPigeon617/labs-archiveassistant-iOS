package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import com.lyihub.archiveassistant.platform.IosNativeBridge

/**
 * iOS implementations delegate to Swift.
 *
 * No Kotlin/Native Foundation bindings are used here: every byte-level operation goes through
 * [IosNativeBridge], which Swift installs at launch with FileManager/Bundle implementations.
 */
actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val path = "$itemsDir/$safeTitle.md"
  IosNativeBridge.callWrite(path, content.encodeToByteArray())
  return path
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

  override suspend fun openRead(): ByteArray? = IosNativeBridge.callRead(sourceKey)
}
