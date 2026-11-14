package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * The topics/items JSON codec, in `commonMain`.
 *
 * The Android build kept this in `:app` on top of `org.json`, which exists only on the JVM. The wire
 * format is deliberately unchanged — a JSON array of flat objects whose keys are the Kotlin property
 * names (`id`, `title`, `iconName`, `iconColor`, `updatedAtEpochMillis` for topics; `id`, `topicId`,
 * `contentType`, `title`, `summary`, `fullText`, `sourceUrl`, `imageResName`, `documentFormat`,
 * `fileName`, `fileSize`, `createdAtEpochMillis` for items) — so a payload written by the Android build
 * still decodes here, and vice versa.
 *
 * Two deliberate choices:
 *
 *  - **Unknown keys are ignored, missing optional keys fall back.** `sourceUrl`, `imageResName`,
 *    `fileName` decode `""` to `null` (the Android `optString(...).takeIf { it.isNotEmpty() }` rule),
 *    `fileSize` accepts absent/null, and an unknown `documentFormat` name decodes to `null` rather
 *    than failing the whole list.
 *  - **Missing required keys are corruption, not a default.** `id`, `topicId`, `contentType`, `title`
 *    and the two timestamps must be present; their absence means the payload was written by something
 *    else, and the caller is told via [AppDataPreferences.DecodeResult.Corrupt] instead of silently
 *    dropping the user's archive. `contentType` follows the Android `valueOf` rule for the same reason:
 *    a value this build cannot name is corruption, not something to guess at.
 */
object AppDataCodec {
  /** File-stem-safe names, also used as the Android DataStore preference keys. */
  const val TopicsKey: String = "app_topics_json"

  const val ItemsKey: String = "app_items_json"

  /** Parse a `JsonElement` tree; kept private so the public surface is string-in/typed-out. */
  private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
  }

  fun encodeTopics(topics: List<Topic>): String =
    buildJsonArray {
        topics.forEach { topic ->
          add(
            buildJsonObject {
              put("id", topic.id)
              put("title", topic.title)
              put("iconName", topic.iconName)
              put("iconColor", topic.iconColor)
              put("updatedAtEpochMillis", topic.updatedAtEpochMillis)
            }
          )
        }
      }
      .toString()

  fun encodeItems(items: List<KnowledgeItem>): String =
    buildJsonArray {
        items.forEach { item ->
          add(
            buildJsonObject {
              put("id", item.id)
              put("topicId", item.topicId)
              put("contentType", item.contentType.name)
              put("title", item.title)
              put("summary", item.summary)
              put("fullText", item.fullText)
              // Nulls are omitted rather than written as JSON null, matching the Android encoder.
              item.sourceUrl?.let { put("sourceUrl", it) }
              item.imageResName?.let { put("imageResName", it) }
              item.documentFormat?.let { put("documentFormat", it.name) }
              item.fileName?.let { put("fileName", it) }
              item.fileSize?.let { put("fileSize", it) }
              put("createdAtEpochMillis", item.createdAtEpochMillis)
            }
          )
        }
      }
      .toString()

  /** Decodes a stored topics payload. `null` (nothing stored yet) is [AppDataPreferences.DecodeResult.Missing]. */
  fun decodeTopics(rawJson: String?): TopicDecodeResult =
    decode(rawJson) { array -> array.map(::parseTopic) }

  /** Decodes a stored items payload; same contract as [decodeTopics]. */
  fun decodeItems(rawJson: String?): ItemDecodeResult =
    decode(rawJson) { array -> array.map(::parseItem) }

  private fun <T> decode(
    rawJson: String?,
    parse: (JsonArray) -> List<T>,
  ): AppDataPreferences.DecodeResult<List<T>> {
    if (rawJson == null) return AppDataPreferences.DecodeResult.Missing
    return try {
      AppDataPreferences.DecodeResult.Valid(parse(json.parseToJsonElement(rawJson).jsonArray))
    } catch (error: Exception) {
      // The raw payload travels with the failure so a later build can attempt a recovery pass.
      AppDataPreferences.DecodeResult.Corrupt(rawJson)
    }
  }

  private fun parseTopic(element: JsonElement): Topic {
    val obj = element as JsonObject
    return Topic(
      id = obj.requiredString("id"),
      title = obj.requiredString("title"),
      iconName = obj.requiredString("iconName"),
      iconColor = obj.requiredString("iconColor"),
      updatedAtEpochMillis = obj.requiredLong("updatedAtEpochMillis"),
    )
  }

  private fun parseItem(element: JsonElement): KnowledgeItem {
    val obj = element as JsonObject
    val rawContentType = obj.requiredString("contentType")
    val contentType =
      ContentType.entries.firstOrNull { it.name == rawContentType }
        ?: error("Unknown contentType '$rawContentType'")
    val rawDocumentFormat = obj.optionalString("documentFormat")
    return KnowledgeItem(
      id = obj.requiredString("id"),
      topicId = obj.requiredString("topicId"),
      contentType = contentType,
      title = obj.requiredString("title"),
      summary = obj.optionalString("summary").orEmpty(),
      fullText = obj.optionalString("fullText").orEmpty(),
      sourceUrl = obj.optionalString("sourceUrl"),
      imageResName = obj.optionalString("imageResName"),
      documentFormat =
        rawDocumentFormat?.let { name -> DocumentFormat.entries.firstOrNull { it.name == name } },
      fileName = obj.optionalString("fileName"),
      fileSize = obj["fileSize"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.longOrNull,
      createdAtEpochMillis = obj.requiredLong("createdAtEpochMillis"),
    )
  }

  private fun JsonObject.requiredString(key: String): String =
    jsonPrimitive(key).content

  private fun JsonObject.requiredLong(key: String): Long = jsonPrimitive(key).longOrNull ?: error("Expected a number at '$key'")

  private fun JsonObject.optionalString(key: String): String? =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.content?.takeIf { it.isNotEmpty() }

  private fun JsonObject.jsonPrimitive(key: String) =
    this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive ?: error("Missing required key '$key'")
}
