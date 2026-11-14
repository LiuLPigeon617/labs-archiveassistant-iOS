package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.platform.PlatformFileStore
import com.lyihub.archiveassistant.platform.platformFileStore
import kotlinx.serialization.json.Json

/**
 * File-backed [AppDataRepository], shared by every platform.
 *
 * The board item asked for one implementation per platform. There is only one, deliberately: the
 * platform-specific part of "persist topics and items" is *where the bytes go*, and that is already
 * abstracted by [PlatformFileStore] (whose `stateDir` is `NSApplicationSupportDirectory` on iOS,
 * `filesDir` on Android and a temp directory on desktop). Writing an `expect`/`actual` pair on top of
 * that would duplicate the same read-modify-write logic three times with no platform detail left in
 * it. `FileStoreContentImporter` and `ImportNaming` in this package make the same call.
 *
 * Layout, one file per payload so a corrupt items file cannot take the topics with it:
 *
 *  ```
 *  <stateDir>/app_topics_json.json
 *  <stateDir>/app_items_json.json
 *  <stateDir>/ai_engine_settings.json
 *  ```
 *
 * Every write is a whole-payload overwrite: [PlatformFileStore] offers no atomic-rename primitive,
 * and the payloads are small (the seeded archive is tens of KB).
 */
class FileAppDataRepository(
  private val fileStore: PlatformFileStore = platformFileStore(),
) : AppDataRepository {

  override suspend fun loadSnapshot(): AppDataSnapshot? {
    if (fileStore.stateDir.isBlank()) return null
    val topicsJson = readText(topicsPath())
    val itemsJson = readText(itemsPath())
    if (topicsJson == null && itemsJson == null) return null
    return AppDataSnapshot(
      topics = AppDataCodec.decodeTopics(topicsJson),
      items = AppDataCodec.decodeItems(itemsJson),
    )
  }

  override suspend fun saveAll(topics: List<Topic>, items: List<KnowledgeItem>) {
    if (fileStore.stateDir.isBlank()) return
    writeText(topicsPath(), AppDataCodec.encodeTopics(topics))
    writeText(itemsPath(), AppDataCodec.encodeItems(items))
  }

  private fun topicsPath(): String = "${fileStore.stateDir}/${AppDataCodec.TopicsKey}.json"

  private fun itemsPath(): String = "${fileStore.stateDir}/${AppDataCodec.ItemsKey}.json"

  private suspend fun readText(path: String): String? =
    fileStore.readBytes(path)?.decodeToString()

  private suspend fun writeText(path: String, text: String) =
    fileStore.writeBytes(path, text.encodeToByteArray())
}

/**
 * File-backed [AiEngineSettingsRepository].
 *
 * Same storage directory as [FileAppDataRepository], separate file: AI settings are written on every
 * settings edit and must not force a rewrite of the archive.
 */
class FileAiEngineSettingsRepository(
  private val fileStore: PlatformFileStore = platformFileStore(),
) : AiEngineSettingsRepository {
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  override suspend fun save(settings: AiEngineSettings) {
    if (fileStore.stateDir.isBlank()) return
    val encoded = json.encodeToString(AiEngineSettings.serializer(), settings)
    fileStore.writeBytes(settingsPath(), encoded.encodeToByteArray())
  }

  override suspend fun load(): AiEngineSettings? {
    if (fileStore.stateDir.isBlank()) return null
    val raw = fileStore.readBytes(settingsPath())?.decodeToString() ?: return null
    // A settings file this build cannot read is not worth failing the launch over: fall back to the
    // defaults the store already holds. The next settings edit overwrites it.
    return runCatching { json.decodeFromString(AiEngineSettings.serializer(), raw) }.getOrNull()
  }

  private fun settingsPath(): String = "${fileStore.stateDir}/ai_engine_settings.json"
}
