package com.lyihub.archiveassistant.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.lyihub.archiveassistant.data.JvmContentSource
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformImageDecoder(): ImageBytesDecoder = DesktopImageDecoder()

/**
 * [decodeToImageBitmap] rather than `org.jetbrains.skia.Image`: the skiko type would put a skiko
 * dependency on this target's compile classpath, and a dependency that only resolves on the macOS
 * CI runner is exactly the kind this machine cannot check. The compose helper is available wherever
 * compose draws, which is every target here.
 *
 * [readSize] therefore pays for a full decode instead of a bounds-only read. Detail view measures one
 * image each time it opens, so the cost lands once per screen, not in a loop.
 */
private class DesktopImageDecoder : ImageBytesDecoder {

  override suspend fun readSize(bytes: ByteArray): ImagePixelSize? =
    withContext(Dispatchers.Default) {
      val image = decode(bytes) ?: return@withContext null
      ImagePixelSize(image.width, image.height)
    }

  override suspend fun decode(bytes: ByteArray): ImageBitmap? =
    withContext(Dispatchers.Default) { runCatching { bytes.decodeToImageBitmap() }.getOrNull() }
}

actual fun platformContentPicker(): ContentPicker = DesktopContentPicker()

/**
 * A Swing file chooser, because the desktop target exists to verify compiles and to run tests.
 *
 * A headless JVM (CI, `desktopTest`) throws `HeadlessException` here; that is caught and reported as
 * a cancelled pick, which is exactly what callers already handle.
 */
private class DesktopContentPicker : ContentPicker {

  override suspend fun pick(allowedExtensions: List<String>): ContentSource? =
    withContext(Dispatchers.IO) {
      runCatching {
          val chooser =
            JFileChooser().apply {
              dialogTitle = "选择文件"
              fileSelectionMode = JFileChooser.FILES_ONLY
              if (allowedExtensions.isNotEmpty()) {
                fileFilter =
                  FileNameExtensionFilter(
                    allowedExtensions.joinToString(", "),
                    *allowedExtensions.toTypedArray(),
                  )
              }
            }
          if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile?.takeIf(File::exists)?.let { JvmContentSource(it.absolutePath) }
          } else {
            null
          }
        }
        .getOrNull()
    }
}
