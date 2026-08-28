package com.lyihub.archiveassistant.platform

import kotlinx.coroutines.flow.Flow

/**
 * A readable, seekable binary source that came from a platform-specific content provider.
 *
 * Android has `Uri` + ContentResolver and iOS has security-scoped bookmarks + file coordination.
 * Both are abstracted behind this so common code deals only with bytes and a display name.
 */
interface ContentSource {
  val displayName: String?

  suspend fun openRead(): ByteArray?
}

/**
 * App-private file storage.
 *
 * Replaces the Android `Context.filesDir` usage that leaked into the state layer. Keeping it behind
 * an interface is what lets `ArchiveAssistantStateStore` become platform-free.
 */
interface PlatformFileStore {
  /** Directory for exported item documents (e.g. generated Markdown). */
  val itemsDir: String

  /** Directory for downloaded/inference model files. */
  val modelsDir: String

  suspend fun exists(path: String): Boolean

  suspend fun writeBytes(path: String, bytes: ByteArray)

  suspend fun readBytes(path: String): ByteArray?
}

/** Logging façade; replaces `android.util.Log` in shared code. */
interface Logger {
  fun d(tag: String, message: String)

  fun w(tag: String, message: String)

  fun e(tag: String, message: String, throwable: Throwable? = null)
}

/** Reads a bundled seed asset (the Android build shipped these under `res/raw`). */
expect class BundledAssetReader() {
  /** Copies a bundled asset into the app's private storage if absent. Returns the local path. */
  suspend fun materialize(assetName: String, outputFileName: String): String?
}

/** Model download progress reporting, unified across platforms. */
data class DownloadProgress(
  val bytesDownloaded: Long,
  val totalBytes: Long,
  val isComplete: Boolean,
)

/** Platform entry points supplied to the shared state store. */
expect fun platformLogger(): Logger

expect fun platformFileStore(): PlatformFileStore
