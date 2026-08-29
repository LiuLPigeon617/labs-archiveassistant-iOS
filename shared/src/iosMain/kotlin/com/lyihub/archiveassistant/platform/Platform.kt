package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.readUrlBytes
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun platformLogger(): Logger = IosLogger()

actual fun platformFileStore(): PlatformFileStore = IosFileStore()

private class IosLogger : Logger {
  override fun d(tag: String, message: String) = println("[$tag] D: $message")

  override fun w(tag: String, message: String) = println("[$tag] W: $message")

  override fun e(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] E: $message ${throwable?.message.orEmpty()}")
  }
}

/**
 * iOS file storage backed by Application Support.
 *
 * Implemented with kotlinx-io rather than raw Foundation calls: it is a Kotlin Multiplatform
 * library already used by this module, it needs no cinterop opt-in, and it keeps the byte handling
 * identical to the JVM/Android implementations.
 */
private class IosFileStore : PlatformFileStore {
  private val appSupportDir: String by lazy {
    val paths =
      NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
    (paths.firstOrNull() as? String) ?: error("Cannot resolve Application Support directory")
  }

  override val itemsDir: String
    get() = ensureDirectory("items")

  override val modelsDir: String
    get() = ensureDirectory("models")

  private fun ensureDirectory(name: String): String {
    val path = "$appSupportDir/$name"
    SystemFileSystem.createDirectories(Path(path))
    return path
  }

  override suspend fun exists(path: String): Boolean =
    runCatching { SystemFileSystem.exists(Path(path)) }.getOrDefault(false)

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    SystemFileSystem.sink(Path(path)).use { sink -> sink.write(bytes) }
  }

  override suspend fun readBytes(path: String): ByteArray? =
    runCatching { SystemFileSystem.source(Path(path)).use { it.readByteArray() } }.getOrNull()
}

actual class BundledAssetReader {
  /**
   * Copies a resource from the app bundle into Application Support.
   *
   * iOS has no `res/raw` + `openRawResource` equivalent; bundle resources are located through
   * [NSBundle.mainBundle] and then written into storage so downstream code can treat them as
   * ordinary files, matching the Android behavior.
   */
  override actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val store = platformFileStore()
    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val name = assetName.substringBeforeLast('.')
    val extension = assetName.substringAfterLast('.', "")
    val url =
      NSBundle.mainBundle.URLForResource(name, withExtension = extension.takeIf { it.isNotEmpty() })
        ?: return null

    // Read the bundle resource through Foundation, then hand the bytes to the shared file store.
    val bytes = readUrlBytes(url) ?: return null
    store.writeBytes(destination, bytes)
    return destination
  }
}
