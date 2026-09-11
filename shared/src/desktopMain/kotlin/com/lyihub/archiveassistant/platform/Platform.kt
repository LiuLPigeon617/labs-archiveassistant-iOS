package com.lyihub.archiveassistant.platform

import java.io.File

actual fun platformLogger(): Logger = JvmLogger()

actual fun platformFileStore(): PlatformFileStore = JvmFileStore()

private class JvmLogger : Logger {
  override fun d(tag: String, message: String) = println("[$tag] D: $message")

  override fun w(tag: String, message: String) = println("[$tag] W: $message")

  override fun e(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] E: $message ${throwable?.message.orEmpty()}")
  }
}

/** JVM target exists for compile verification and unit tests; storage is rooted in a temp dir. */
private class JvmFileStore : PlatformFileStore {
  private val root: File = File(System.getProperty("java.io.tmpdir"), "archiveassistant").also {
    it.mkdirs()
  }

  override val itemsDir: String
    get() = File(root, "items").also { it.mkdirs() }.absolutePath

  override val modelsDir: String
    get() = File(root, "models").also { it.mkdirs() }.absolutePath

  override suspend fun exists(path: String): Boolean = File(path).exists()

  override suspend fun writeBytes(path: String, bytes: ByteArray) {
    File(path).also { it.parentFile?.mkdirs() }.writeBytes(bytes)
  }

  override suspend fun readBytes(path: String): ByteArray? =
    File(path).takeIf { it.exists() }?.readBytes()
}

/**
 * JVM has no app bundle; seed assets are resolved from the classpath so tests can supply fixtures.
 */
actual class BundledAssetReader {
  actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val store = platformFileStore()
    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val bytes =
      this::class.java.classLoader.getResourceAsStream(assetName)?.use { it.readBytes() }
        ?: return null
    store.writeBytes(destination, bytes)
    return destination
  }
}
