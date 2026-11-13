package com.lyihub.archiveassistant.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * The two platform seams that DetailPane still lacks: image decoding and the file picker.
 *
 * The Android original reaches for `BitmapFactory` and
 * `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())`, neither of which
 * exists outside `:app`. Both are expressed here in terms of bytes and [ContentSource], so the
 * screen itself can move to `commonMain` without ever naming an Android type.
 *
 * Every entry point takes or returns plain data on purpose: a `Uri` cannot cross the Kotlin/Native
 * boundary, and a `Bitmap` is not a multiplatform type. `ImageBitmap` is — it is the one image type
 * Compose itself draws on all four targets.
 *
 * Implementations are expected to be cheap to construct and safe to call from any dispatcher; the
 * decoder runs its work off the caller's thread.
 */

/** Intrinsic pixel dimensions of an encoded image. */
data class ImagePixelSize(val width: Int, val height: Int)

/**
 * Decodes encoded image bytes (PNG/JPEG/…) into something Compose can draw.
 *
 * Split in two because DetailPane needs both halves separately: it measures an image first to pick
 * an aspect ratio (without paying for a full decode), and only then decodes the bytes it displays.
 */
interface ImageBytesDecoder {
  /** Reads the dimensions without keeping the decoded pixels. Returns null when the bytes are not an image. */
  suspend fun readSize(bytes: ByteArray): ImagePixelSize?

  /** Full decode for display. Returns null when the bytes are not an image. */
  suspend fun decode(bytes: ByteArray): ImageBitmap?
}

/** The platform's image decoder. */
expect fun platformImageDecoder(): ImageBytesDecoder

/**
 * The platform's own file picker.
 *
 * Returns null when the user cancels, and also when the platform has no picker wired up yet — the
 * call site already treats "nothing came back" as a no-op, so an unwired host degrades to a button
 * that does nothing rather than a crash.
 *
 * [allowedExtensions] are lowercase, without the dot (`pdf`, `png`). An empty list means any file.
 */
interface ContentPicker {
  suspend fun pick(allowedExtensions: List<String>): ContentSource?
}

/** The platform's file picker. */
expect fun platformContentPicker(): ContentPicker
