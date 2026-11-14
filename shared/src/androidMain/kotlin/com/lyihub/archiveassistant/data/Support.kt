package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.AndroidPlatformContext
import com.lyihub.archiveassistant.platform.ContentSource
import java.io.File

actual fun writeMarkdownFile(itemsDir: String, title: String, content: String): String? {
  val dir = File(itemsDir).also { it.mkdirs() }
  val safeTitle = title.replace(Regex("""[\\/:*?"<>|]"""), "_").take(60).ifBlank { "untitled" }
  val file = File(dir, "$safeTitle.md")
  file.writeText(content)
  return file.absolutePath
}

/** Android resolves a content Uri through ContentResolver. */
class AndroidContentSource(override val sourceKey: String) : ContentSource {
  override val displayName: String?
    get() =
      runCatching {
          val uri = android.net.Uri.parse(sourceKey)
          AndroidPlatformContext.require().contentResolver
            .query(uri, null, null, null, null)
            ?.use { cursor ->
              val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
              if (cursor.moveToFirst() && index >= 0) cursor.getString(index) else null
            }
        }
        .getOrNull()

  override suspend fun openRead(): ByteArray? =
    runCatching {
        val uri = android.net.Uri.parse(sourceKey)
        AndroidPlatformContext.require().contentResolver.openInputStream(uri)?.use { it.readBytes() }
      }
      .getOrNull()
}
