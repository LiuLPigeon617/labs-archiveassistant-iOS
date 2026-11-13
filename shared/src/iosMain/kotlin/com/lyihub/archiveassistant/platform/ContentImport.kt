package com.lyihub.archiveassistant.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.decodeToImageBitmap
import com.lyihub.archiveassistant.data.IosContentSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

actual fun platformImageDecoder(): ImageBytesDecoder = IosImageDecoder()

/**
 * The same three lines as the desktop decoder, and deliberately not shared with it.
 *
 * A `skikoMain` source set holding both would be tidier, but this machine can only compile the
 * desktop target, so a source-set-hierarchy change here would be unverifiable until a macOS CI run
 * thirty minutes later. Six duplicated lines are the cheaper risk. Both sides go through
 * [decodeToImageBitmap], which compose implements on every target it draws on.
 *
 * `readSize` pays for a full decode; see the desktop copy for why that trade is acceptable.
 */
private class IosImageDecoder : ImageBytesDecoder {

  override suspend fun readSize(bytes: ByteArray): ImagePixelSize? {
    val image = decode(bytes) ?: return null
    return ImagePixelSize(image.width, image.height)
  }

  override suspend fun decode(bytes: ByteArray): ImageBitmap? =
    runCatching { bytes.decodeToImageBitmap() }.getOrNull()
}

actual fun platformContentPicker(): ContentPicker = IosContentPicker()

/**
 * The picker itself is Swift's: `UIDocumentPickerViewController` must be presented by the hosting
 * view controller, which Kotlin/Native cannot reach safely from shared code.
 *
 * [IosNativeBridge.pickFile] is called with the allowed extensions and a completion callback; if
 * Swift has not installed a handler the pick resolves to "cancelled" rather than to an exception.
 */
private class IosContentPicker : ContentPicker {

  override suspend fun pick(allowedExtensions: List<String>): ContentSource? {
    val handler = IosNativeBridge.pickFile ?: return null
    return suspendCancellableCoroutine { continuation ->
      handler(allowedExtensions) { path ->
        if (continuation.isActive) {
          continuation.resume(path?.takeIf { it.isNotBlank() }?.let(::IosContentSource))
        }
      }
    }
  }
}
