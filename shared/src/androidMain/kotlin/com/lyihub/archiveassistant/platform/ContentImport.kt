package com.lyihub.archiveassistant.platform

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

actual fun platformImageDecoder(): ImageBytesDecoder = AndroidImageDecoder()

/** BitmapFactory, hidden behind [ImageBytesDecoder] so no `Bitmap` reaches shared screen code. */
private class AndroidImageDecoder : ImageBytesDecoder {

  override suspend fun readSize(bytes: ByteArray): ImagePixelSize? =
    withContext(Dispatchers.Default) {
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
      // decodeByteArray returns null in bounds-only mode; the dimensions land in the options.
      if (options.outWidth <= 0 || options.outHeight <= 0) {
        null
      } else {
        ImagePixelSize(options.outWidth, options.outHeight)
      }
    }

  override suspend fun decode(bytes: ByteArray): ImageBitmap? =
    withContext(Dispatchers.Default) {
      BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
}

/**
 * Where the Android host publishes its picker.
 *
 * `rememberLauncherForActivityResult` only works inside a composable with an activity host, so the
 * shared module cannot construct one. The host builds a launcher-backed [ContentPicker] and assigns
 * it here; until it does, [platformContentPicker] hands back one that always cancels.
 *
 * This is the same injection shape as [AndroidPlatformContext]: one volatile slot, filled at startup.
 */
object AndroidContentPickerHost {
  @Volatile var picker: ContentPicker? = null

  fun install(picker: ContentPicker) {
    this.picker = picker
  }
}

actual fun platformContentPicker(): ContentPicker =
  AndroidContentPickerHost.picker ?: CancellingContentPicker

private object CancellingContentPicker : ContentPicker {
  override suspend fun pick(allowedExtensions: List<String>): ContentSource? = null
}
