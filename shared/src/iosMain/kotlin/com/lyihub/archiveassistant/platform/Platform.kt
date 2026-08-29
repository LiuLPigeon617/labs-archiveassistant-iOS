package com.lyihub.archiveassistant.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile

actual fun platformLogger(): Logger = IosLogger()

actual fun platformFileStore(): PlatformFileStore = IosFileStore()

private class IosLogger : Logger {
  override fun d(tag: String, message: String) = println("[$tag] D: $message")

  override fun w(tag: String, message: String) = println("[$tag] W: $message")

  override fun e(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] E: $message ${throwable?.message.orEmpty()}")
  }
}

private class IosFileStore : PlatformFileStore {
  private val fileManager = NSFileManager.defaultManager

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
    if (!fileManager.fileExistsAtPath(path)) {
      fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, null, null)
    }
    return path
  }

  override suspend fun exists(path: String): Boolean = fileManager.fileExistsAtPath(path)

  override suspend fun writeBytes(path: String, bytes: ByteArray) =
    bytes.toNSData().writeToFile(path, atomically = true)

  override suspend fun readBytes(path: String): ByteArray? =
    NSData.dataWithContentsOfFile(path)?.toByteArray()
}

/**
 * Wraps a Kotlin [ByteArray] as [NSData].
 *
 * `memScoped` + `allocArrayOf` is the documented Kotlin/Native idiom: the C array is allocated in
 * the scope and copied by NSData before the scope exits.
 */
@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toNSData(): NSData =
  memScoped {
    NSData.create(
      bytes = allocArrayOf(this@toNSData),
      length = this@toNSData.size.toULong(),
    )
  }

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  if (size == 0) return ByteArray(0)
  return ByteArray(size).apply {
    usePinned { pinned -> this@toByteArray.getBytes(pinned.addressOf(0), length = size.toULong()) }
  }
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

    val data = NSData.dataWithContentsOfURL(url) ?: return null
    store.writeBytes(destination, data.toByteArray())
    return destination
  }
}
