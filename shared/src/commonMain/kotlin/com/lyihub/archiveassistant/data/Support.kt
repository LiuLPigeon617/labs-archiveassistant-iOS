package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.AiEngineSettings
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic

/** Writes `content` to a Markdown file inside `itemsDir`, returning the written path. */
expect fun writeMarkdownFile(itemsDir: String, title: String, content: String): String?

/**
 * Persists application data (topics + items).
 *
 * The Android build used DataStore Preferences with a hand-written JSON codec. That codec relies on
 * `org.json` and is therefore JVM-only, so each platform supplies its own implementation behind this
 * narrow contract.
 */
interface AppDataRepository {
  suspend fun loadSnapshot(): AppDataSnapshot?

  suspend fun saveAll(topics: List<Topic>, items: List<KnowledgeItem>)
}

data class AppDataSnapshot(
  val topics: AppDataPreferences.DecodeResult<List<Topic>>,
  val items: AppDataPreferences.DecodeResult<List<KnowledgeItem>>,
)

interface AiEngineSettingsRepository {
  suspend fun save(settings: AiEngineSettings)
}
