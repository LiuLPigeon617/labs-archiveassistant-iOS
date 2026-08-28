package com.lyihub.archiveassistant.platform

import android.content.Context
import android.util.Log
import java.io.File

/** Set once from Application.onCreate; the shared module has no other way to reach a Context. */
object AndroidPlatformContext {
  @Volatile var applicationContext: Context? = null

  fun require(): Context =
    requireNotNull(applicationContext) { "AndroidPlatformContext.applicationContext not set" }
}

actual fun platformLogger(): Logger = AndroidLogger()

actual fun platformFileStore(): PlatformFileStore = AndroidFileStore()

private class AndroidLogger : Logger {
  override fun d(tag: String, message: String) = Log.d(tag, message)

  override fun w(tag: String, message: String) = Log.w(tag, message)

  override fun e(tag: String, message: String, throwable: Throwable?) {
    Log.e(tag, message, throwable)
  }
}

private class AndroidFileStore : PlatformFileStore {
  private val filesDir: File
    get() = AndroidPlatformContext.require().filesDir

  override val itemsDir: String
    get() = File(filesDir, "items").also { it.mkdirs() }.absolutePath

  override val modelsDir: String
    get() = File(filesDir, "models").also { it.mkdirs() }.absolutePath

  override suspend fun exists(path: String): Boolean = File(path).exists()

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    File(path).also { it.parentFile?.mkdirs() }.writeBytes(bytes)
  }

  override suspend fun readBytes(path: String): ByteArray? =
    File(path).takeIf { it.exists() }?.readBytes()
}

actual class BundledAssetReader {
  /**
   * Copies a `res/raw` asset into private storage, matching the legacy Android behavior of
   * `resolveMockResourcePaths()` in the old state store.
   */
  actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val context = AndroidPlatformContext.require()
    val store = platformFileStore()
    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val resId =
      context.resources.getIdentifier(
        assetName.substringBeforeLast('.'),
        "raw",
        context.packageName,
      )
    if (resId == 0) return null

    val bytes = context.resources.openRawResource(resId).use { it.readBytes() }
    store.writeBytes(destination, bytes)
    return destination
  }
}
