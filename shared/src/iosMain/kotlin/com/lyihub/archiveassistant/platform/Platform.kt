package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.readUrlBytes
import kotlinx.io.buffered
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
 * library already used by this module, needs no cinterop opt-in, and keeps byte handling identical
 * to the JVM/Android implementations.
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
    SystemFileSystem.sink(Path(path)).buffered().use { sink ->
      sink.write(bytes, 0, bytes.size)
      sink.flush()
    }
  }

  override suspend fun readBytes(path: String): ByteArray? =
    runCatching {
        val file = Path(path)
        val size = SystemFileSystem.metadataOrNull(file)?.size
        if (size == null || size == 0L) {
          ByteArray(0)
        } else {
          SystemFileSystem.source(file).buffered().use { source ->
            source.readByteArray(size.toInt())
          }
        }
      }
      .getOrNull()
}

/**
 * Copies a bundled resource into Application Support.
 *
 * iOS has no `res/raw` + `openRawResource` equivalent; bundle resources are located through
 * [NSBundle.mainBundle] and then written into storage so downstream code can treat them as
 * ordinary files, matching the Android behavior.
 */
actual class BundledAssetReader actual constructor() {
  actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val store = platformFileStore()
    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val name = assetName.substringBeforeLast('.')
    val extension = assetName.substringAfterLast('.', "")
    val url =
      NSBundle.mainBundle.URLForResource(name, withExtension = extension.takeIf { it.isNotEmpty() })
        ?: return null

    val bytes = readUrlBytes(url) ?: return null
    store.writeBytes(destination, bytes)
    return destination
  }
}
