package com.lyihub.archiveassistant.platform

import com.lyihub.archiveassistant.data.readFileBytes
import com.lyihub.archiveassistant.data.readUrlBytes
import com.lyihub.archiveassistant.data.writeFileBytes
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
 * Byte handling is delegated to the shared kotlinx-io helpers in `data/Support.kt` so there is a
 * single implementation across JVM, Android and iOS.
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

  override suspend fun writeBytes(path: String, bytes: ByteArray) = writeFileBytes(path, bytes)

  override suspend fun readBytes(path: String): ByteArray? = readFileBytes(path)
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
