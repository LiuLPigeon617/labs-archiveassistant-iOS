package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.AiEngineType
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.platform.PlatformFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the persistence wiring that did not exist before: the store had two repository slots and
 * both defaulted to null, so loadPersistedStateAsync and saveData returned on their first line and
 * every launch started again from the seeded sample data.
 *
 * The file system is faked (Kotlin/Native has no usable one in a test) but the *paths* are not: the
 * tests assert where the bytes land, because the whole persistence contract is "the same app opens
 * the same archive", and a repository that quietly wrote to a blank directory would pass a
 * round-trip test while still losing the user's data on device.
 */
class FileAppDataRepositoryTest {

  private class FakeFileStore(override val stateDir: String = "/state") : PlatformFileStore {
    val files = mutableMapOf<String, String>()

    override val itemsDir: String = "/items"
    override val modelsDir: String = "/models"

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
      files[path] = bytes.decodeToString()
    }

    override suspend fun readBytes(path: String): ByteArray? = files[path]?.encodeToByteArray()
  }

  private val topic = Topic("t1", "中书省", "ic_zhongshu", "#8C6239", 1_700_000_000_000L)

  private val item =
    KnowledgeItem(
      id = "i1",
      topicId = "t1",
      contentType = ContentType.DOCUMENT,
      title = "标题",
      summary = "摘要",
      fullText = "正文",
      sourceUrl = null,
      documentFormat = null,
      fileName = "a.pdf",
      fileSize = 10L,
      createdAtEpochMillis = 1_700_000_000_001L,
    )

  @Test
  fun anEmptyStoreLoadsNothing() = runTest {
    assertNull(FileAppDataRepository(FakeFileStore()).loadSnapshot())
  }

  @Test
  fun savedDataIsReadBackOnTheNextLoad() = runTest {
    val store = FakeFileStore()
    val repository = FileAppDataRepository(store)

    repository.saveAll(listOf(topic), listOf(item))

    val snapshot = assertIs<AppDataSnapshot>(repository.loadSnapshot())
    assertEquals(
      listOf(topic),
      assertIs<AppDataPreferences.DecodeResult.Valid<List<Topic>>>(snapshot.topics).value,
    )
    assertEquals(
      listOf(item),
      assertIs<AppDataPreferences.DecodeResult.Valid<List<KnowledgeItem>>>(snapshot.items).value,
    )
  }

  @Test
  fun payloadsGoToTheStateDirectoryOnePerFile() = runTest {
    val store = FakeFileStore()
    FileAppDataRepository(store).saveAll(listOf(topic), listOf(item))

    assertEquals(
      setOf("/state/app_topics_json.json", "/state/app_items_json.json"),
      store.files.keys,
    )
  }

  @Test
  fun aBlankStateDirectoryDisablesPersistenceInsteadOfThrowing() = runTest {
    // iOS reports a blank stateDir when Swift has not installed the handler yet; the app must then
    // behave exactly as it did before persistence existed rather than write to a relative path.
    val store = FakeFileStore(stateDir = "")
    val repository = FileAppDataRepository(store)

    repository.saveAll(listOf(topic), listOf(item))

    assertTrue(store.files.isEmpty(), "no file should be written when stateDir is blank")
    assertNull(repository.loadSnapshot())
  }

  @Test
  fun aCorruptTopicsFileSurfacesAsCorruptWithoutHidingTheItems() = runTest {
    val store = FakeFileStore()
    val repository = FileAppDataRepository(store)
    repository.saveAll(listOf(topic), listOf(item))
    store.files["/state/app_topics_json.json"] = "[{"

    val snapshot = assertIs<AppDataSnapshot>(repository.loadSnapshot())

    assertIs<AppDataPreferences.DecodeResult.Corrupt>(snapshot.topics)
    assertEquals(
      listOf(item),
      assertIs<AppDataPreferences.DecodeResult.Valid<List<KnowledgeItem>>>(snapshot.items).value,
    )
  }

  @Test
  fun onlyTheItemsFilePresentStillLoads() = runTest {
    val store = FakeFileStore()
    FileAppDataRepository(store).saveAll(emptyList(), listOf(item))
    // saveAll writes both files; drop the topics one to model a partially written payload.
    store.files.remove("/state/app_topics_json.json")

    val snapshot = assertIs<AppDataSnapshot>(FileAppDataRepository(store).loadSnapshot())

    assertEquals(AppDataPreferences.DecodeResult.Missing, snapshot.topics)
  }

  @Test
  fun aiSettingsRoundTrip() = runTest {
    val store = FakeFileStore()
    val repository = FileAiEngineSettingsRepository(store)
    val settings =
      AiEngineSettings(
        engineType = AiEngineType.ANTHROPIC,
        baseUrl = "https://api.anthropic.com/v1",
        modelName = "claude-sonnet-4",
        apiKey = "secret",
      )

    repository.save(settings)

    assertEquals(settings, repository.load())
    assertEquals(setOf("/state/ai_engine_settings.json"), store.files.keys)
  }

  @Test
  fun unreadableAiSettingsFallBackToNull() = runTest {
    val store = FakeFileStore()
    store.files["/state/ai_engine_settings.json"] = "not json"

    assertNull(FileAiEngineSettingsRepository(store).load())
  }

  @Test
  fun aiSettingsAreIndependentOfArchiveData() = runTest {
    // Settings are written on every settings edit; the archive must not be rewritten for that, and
    // a corrupt archive must not block the settings read.
    val store = FakeFileStore()
    store.files["/state/app_topics_json.json"] = "[{"

    FileAiEngineSettingsRepository(store).save(AiEngineSettings(apiKey = "k"))

    assertEquals("k", FileAiEngineSettingsRepository(store).load()?.apiKey)
  }
}
