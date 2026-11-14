package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.data.AppDataPreferences.DecodeResult
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the topics/items codec that used to live in the Android module on top of `org.json`.
 *
 * The point of these tests is not "JSON round-trips" (that is nearly free) but the three behaviours
 * that are easy to lose in a rewrite and expensive when lost: the exact key names (an
 * Android-written payload must still load), the "empty string means absent" rule for optional
 * fields, and the decision that a missing required key is corruption rather than an empty list.
 */
class AppDataCodecTest {

  private val topic =
    Topic(
      id = "topic-1",
      title = "中书省",
      iconName = "ic_zhongshu",
      iconColor = "#8C6239",
      updatedAtEpochMillis = 1_700_000_000_000L,
    )

  private val item =
    KnowledgeItem(
      id = "item-1",
      topicId = "topic-1",
      contentType = ContentType.DOCUMENT,
      title = "奏折样例",
      summary = "摘要",
      fullText = "全文",
      sourceUrl = "https://example.com/a",
      imageResName = "sample.png",
      documentFormat = DocumentFormat.PDF,
      fileName = "a.pdf",
      fileSize = 2048L,
      createdAtEpochMillis = 1_700_000_000_001L,
    )

  // Encoding

  @Test
  fun encodedTopicUsesTheAndroidKeyNames() {
    val json = AppDataCodec.encodeTopics(listOf(topic))

    listOf("id", "title", "iconName", "iconColor", "updatedAtEpochMillis").forEach { key ->
      assertTrue(json.contains(key), "encoded topics payload is missing key " + key + ": " + json)
    }
  }

  @Test
  fun encodedItemUsesTheAndroidKeyNames() {
    val json = AppDataCodec.encodeItems(listOf(item))

    listOf(
        "id",
        "topicId",
        "contentType",
        "title",
        "summary",
        "fullText",
        "sourceUrl",
        "imageResName",
        "documentFormat",
        "fileName",
        "fileSize",
        "createdAtEpochMillis",
      )
      .forEach { key ->
        assertTrue(json.contains(key), "encoded items payload is missing key " + key + ": " + json)
      }
  }

  @Test
  fun encodingOmitsNullOptionalsInsteadOfWritingNull() {
    val bare =
      item.copy(
        sourceUrl = null,
        imageResName = null,
        documentFormat = null,
        fileName = null,
        fileSize = null,
      )
    val json = AppDataCodec.encodeItems(listOf(bare))

    listOf("sourceUrl", "imageResName", "documentFormat", "fileName", "fileSize").forEach { key ->
      assertTrue(!json.contains(key), "null " + key + " should be omitted, payload was " + json)
    }
    assertTrue(!json.contains("null"), "payload should contain no JSON null: " + json)
  }

  // Decoding

  @Test
  fun topicRoundTrips() {
    val decoded = AppDataCodec.decodeTopics(AppDataCodec.encodeTopics(listOf(topic)))

    assertEquals(listOf(topic), assertIs<DecodeResult.Valid<List<Topic>>>(decoded).value)
  }

  @Test
  fun itemRoundTrips() {
    val decoded = AppDataCodec.decodeItems(AppDataCodec.encodeItems(listOf(item)))

    assertEquals(listOf(item), assertIs<DecodeResult.Valid<List<KnowledgeItem>>>(decoded).value)
  }

  @Test
  fun nullPayloadIsMissingRatherThanCorrupt() {
    assertEquals(DecodeResult.Missing, AppDataCodec.decodeTopics(null))
    assertEquals(DecodeResult.Missing, AppDataCodec.decodeItems(null))
  }

  @Test
  fun emptyArrayIsValid() {
    assertEquals(DecodeResult.Valid(emptyList<Topic>()), AppDataCodec.decodeTopics("[]"))
  }

  @Test
  fun corruptJsonKeepsTheRawPayload() {
    val raw = "[{\"id\": "
    val decoded = AppDataCodec.decodeTopics(raw)

    assertEquals(raw, assertIs<DecodeResult.Corrupt>(decoded).rawJson)
  }

  @Test
  fun aNonArrayPayloadIsCorrupt() {
    assertIs<DecodeResult.Corrupt>(AppDataCodec.decodeItems("{\"items\": []}"))
  }

  @Test
  fun missingRequiredKeyIsCorruptNotAnEmptyList() {
    // Dropping this to an empty list would look like "the user has no topics", and the store would
    // then seed the sample archive over a payload it simply failed to read.
    assertIs<DecodeResult.Corrupt>(
      AppDataCodec.decodeTopics("[{\"id\": \"a\", \"title\": \"t\"}]")
    )
  }

  @Test
  fun unknownContentTypeIsCorrupt() {
    val payload =
      """[{"id":"i","topicId":"t","contentType":"HOLOGRAM","title":"x","summary":"","fullText":"",
         "createdAtEpochMillis":1}]"""
    assertIs<DecodeResult.Corrupt>(AppDataCodec.decodeItems(payload))
  }

  // Lenient optional handling, matching the Android optString(...).takeIf { isNotEmpty() } rules

  @Test
  fun emptyOptionalStringsDecodeToNull() {
    val payload =
      """[{"id":"i","topicId":"t","contentType":"WEB_ARTICLE","title":"x","summary":"","fullText":"",
         "sourceUrl":"","imageResName":"","fileName":"","createdAtEpochMillis":7}]"""

    val decoded =
      assertIs<DecodeResult.Valid<List<KnowledgeItem>>>(AppDataCodec.decodeItems(payload)).value.single()

    assertNull(decoded.sourceUrl)
    assertNull(decoded.imageResName)
    assertNull(decoded.fileName)
    assertNull(decoded.fileSize)
    assertNull(decoded.documentFormat)
    assertEquals(ContentType.WEB_ARTICLE, decoded.contentType)
    assertEquals(7L, decoded.createdAtEpochMillis)
  }

  @Test
  fun unknownDocumentFormatFallsBackToNull() {
    val payload =
      """[{"id":"i","topicId":"t","contentType":"DOCUMENT","title":"x","summary":"","fullText":"",
         "documentFormat":"EPUB","createdAtEpochMillis":7}]"""

    val decoded =
      assertIs<DecodeResult.Valid<List<KnowledgeItem>>>(AppDataCodec.decodeItems(payload)).value.single()

    assertNull(decoded.documentFormat)
  }

  @Test
  fun unknownKeysAreIgnored() {
    val payload =
      """[{"id":"i","topicId":"t","contentType":"IMAGE_SCREENSHOT","title":"x","summary":"s",
         "fullText":"f","createdAtEpochMillis":7,"legacyFlag":true}]"""

    val decoded =
      assertIs<DecodeResult.Valid<List<KnowledgeItem>>>(AppDataCodec.decodeItems(payload)).value.single()

    assertEquals("i", decoded.id)
  }

  @Test
  fun aRealAndroidPayloadStillDecodes() {
    // Shape copied from the Android encoder: optional keys simply absent rather than null.
    val payload =
      """[{"id":"m1","topicId":"zhongshu","contentType":"WEB_ARTICLE","title":"标题","summary":"摘要",
         "fullText":"正文","createdAtEpochMillis":1700000000000}]"""

    val decoded =
      assertIs<DecodeResult.Valid<List<KnowledgeItem>>>(AppDataCodec.decodeItems(payload)).value.single()

    assertEquals("m1", decoded.id)
    assertNull(decoded.sourceUrl)
    assertNull(decoded.documentFormat)
  }
}
