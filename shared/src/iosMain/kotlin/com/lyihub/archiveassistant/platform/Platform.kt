package com.lyihub.archiveassistant.platform

/**
 * Handlers implemented in Swift and injected at app launch.
 *
 * File I/O and bundle access are delegated to Swift rather than written against the Kotlin/Native
 * Foundation bindings. Swift has first-class `FileManager` and `Bundle` APIs, which removes an
 * entire class of cinterop signature errors and the need for `ExperimentalForeignApi` opt-ins.
 *
 * Swift installs these from `AppDelegate.application(_:didFinishLaunchingWithOptions:)` via
 * `IosPlatformBootstrap.install()`.
 */
object IosNativeBridge {
  var exists: ((path: String) -> Boolean)? = null
  var writeBytes: ((path: String, bytes: ByteArray) -> Unit)? = null
  var readBytes: ((path: String) -> ByteArray?)? = null
  var itemsDir: (() -> String)? = null
  var modelsDir: (() -> String)? = null
  var bundleResourcePath: ((name: String, extension: String) -> String?)? = null

  /** True once Swift has installed the minimum set of handlers. */
  val isReady: Boolean
    get() = exists != null && writeBytes != null && readBytes != null && itemsDir != null

  internal fun callExists(path: String): Boolean = exists?.invoke(path) ?: false

  internal fun callWrite(path: String, bytes: ByteArray) {
    val handler = writeBytes
    if (handler == null) {
      println("[IosNativeBridge] writeBytes not installed; dropping write to $path")
      return
    }
    handler(path, bytes)
  }

  internal fun callRead(path: String): ByteArray? = readBytes?.invoke(path)
}

private class IosLogger : Logger {
  override fun d(tag: String, message: String) = println("[$tag] D: $message")

  override fun w(tag: String, message: String) = println("[$tag] W: $message")

  override fun e(tag: String, message: String, throwable: Throwable?) {
    println("[$tag] E: $message ${throwable?.message.orEmpty()}")
  }
}

actual fun platformLogger(): Logger = IosLogger()

actual fun platformFileStore(): PlatformFileStore = IosFileStore()

private class IosFileStore : PlatformFileStore {
  override val itemsDir: String
    get() = IosNativeBridge.itemsDir?.invoke().orEmpty()

  override val modelsDir: String
    get() = IosNativeBridge.modelsDir?.invoke().orEmpty()

  override suspend fun exists(path: String): Boolean = IosNativeBridge.callExists(path)

  override suspend fun writeBytes(path: String, bytes: ByteArray) =
    IosNativeBridge.callWrite(path, bytes)

  override suspend fun readBytes(path: String): ByteArray? = IosNativeBridge.callRead(path)
}

/**
 * Locates a bundled resource and copies it into private storage.
 *
 * Swift resolves the resource path through `Bundle.main`; Kotlin then copies the bytes through the
 * shared [PlatformFileStore]. All Foundation interaction stays on the Swift side.
 */
actual class BundledAssetReader actual constructor() {
  actual suspend fun materialize(assetName: String, outputFileName: String): String? {
    val store = platformFileStore()
    if (store.itemsDir.isBlank()) return null

    val destination = "${store.itemsDir}/$outputFileName"
    if (store.exists(destination)) return destination

    val name = assetName.substringBeforeLast('.')
    val extension = assetName.substringAfterLast('.', "")
    val sourcePath = IosNativeBridge.bundleResourcePath?.invoke(name, extension) ?: return null
    val bytes = IosNativeBridge.callRead(sourcePath) ?: return null

    store.writeBytes(destination, bytes)
    return destination
  }
}
