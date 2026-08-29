package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import java.io.File

/**
 * JVM implementation for compile verification and tests.
 *
 * JVM has no content providers, so a [ContentSource] key is treated as a plain file path.
 */
actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val dir = File(itemsDir).also { it.mkdirs() }
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val file = File(dir, "$safeTitle.md")
  file.writeText(content)
  return file.absolutePath
}

class JvmContentSource(override val sourceKey: String) : ContentSource {
  override val displayName: String?
    get() = File(sourceKey).takeIf { it.exists() }?.name

  override suspend fun openRead(): ByteArray? =
    File(sourceKey).takeIf { it.exists() }?.readBytes()
}
