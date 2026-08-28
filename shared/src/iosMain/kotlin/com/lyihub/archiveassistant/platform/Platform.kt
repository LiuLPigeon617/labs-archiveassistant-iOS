package com.lyihub.archiveassistant.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSStringFromClass
import platform.Foundation.NSUserDomainMask
import platform.foundation.NSBundle
import platform.Foundation.NSURL

actual fun platformLogger(): Logger = IosLogger()

actual fun platformFileStore(): PlatformFileStore = IosFileStore()

private class IosLogger : Logger {
  override fun d(tag: String, message: String) = println("[$tag] D: $message")

  override fun w(tag: String, message: String) = println("[$tag] W: $message")

  override fun e(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] E: $message ${throwable?.message.orEmpty()}")
  }
}

@OptIn(ExperimentalForeignApi::class)
private class IosFileStore : PlatformFileStore {
  private val fileManager = NSFileManager.defaultManager

  private val appSupportDir: String =
    NSSearchPathForDirectoriesInDomains(
        NSApplicationSupportDirectory,
        NSUserDomainMask,
        true,
      )
      .firstOrNull() as? String ?: error("Cannot resolve Application Support directory")

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

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    val data = bytes.usePinned { pinned ->
      NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    data.writeToFile(path, atomically = true)
  }

  override suspend fun readBytes(path: String): ByteArray? {
    val data = NSData.dataWithContentsOfFile(path) ?: return null
    return data.toByteArray()
  }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
  val size = length.toInt()
  val result = ByteArray(size)
  if (size == 0) return result
  result.usePinned { pinned -> getBytes(pinned.addressOf(0), length = size.toULong()) }
  return result
}

@OptIn(ExperimentalForeignApi::class)
actual class BundledAssetReader {
  /**
   * Copies a resource from the app bundle into Application Support.
   *
   * iOS has no `res/raw` + `openRawResource` equivalent; bundle resources are located via
   * [NSBundle.mainBundle] and then materialized into writable storage so downstream code can treat
   * them as ordinary files, matching the Android behavior.
   */
  actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val store = platformFileStore()
    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val bundle = NSBundle.mainBundle
    val name = assetName.substringBeforeLast('.')
    val extension = assetName.substringAfterLast('.', "")
    val url =
      bundle.URLForResource(name, withExtension = extension.ifEmpty { null }) ?: return null

    val data = NSData.dataWithContentsOfURL(url) ?: return null
    store.writeBytes(destination, data.toByteArray())
    return destination
  }
}
