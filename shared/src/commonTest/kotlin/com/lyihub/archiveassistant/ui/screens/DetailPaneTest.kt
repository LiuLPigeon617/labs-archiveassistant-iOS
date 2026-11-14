package com.lyihub.archiveassistant.ui.screens

import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The detail wall is pure geometry and string work, so it can be pinned on the one target this
 * project can run locally. Everything asserted here is a rule the Android original had and that a
 * port is easy to get subtly wrong: the column split, the height estimate, and which tags appear.
 */
class DetailPaneTest {

  private fun item(
    id: String,
    title: String = "标题",
    summary: String = "",
    fullText: String = "",
    contentType: ContentType = ContentType.WEB_ARTICLE,
    documentFormat: DocumentFormat? = null,
    imageResName: String? = null,
  ) = KnowledgeItem(
    id = id,
    topicId = "treasury",
    contentType = contentType,
    title = title,
    summary = summary,
    fullText = fullText,
    sourceUrl = null,
    imageResName = imageResName,
    documentFormat = documentFormat,
    fileName = null,
    fileSize = null,
    createdAtEpochMillis = 0L,
  )

  @Test
  fun distributePreservesEveryItemExactlyOnce() {
    val items = (1..9).map { item("id-$it", title = "标题$it") }
    val columns = distributeArticleCards(items)

    assertEquals(2, columns.size)
    val flattened = columns.flatMap { it.map { card -> card.item.id } }
    assertEquals(items.size, flattened.size)
    assertEquals(items.map { it.id }.toSet(), flattened.toSet())
  }

  @Test
  fun distributeKeepsInputOrderInsideEachColumn() {
    val items = (1..6).map { item("id-$it") }
    val columns = distributeArticleCards(items)

    columns.forEach { column ->
      val ids = column.map { it.item.id }
      assertEquals(ids.sortedBy { items.indexOfFirst { source -> source.id == it } }, ids)
    }
  }

  @Test
  fun distributeIsDeterministic() {
    val items = (1..12).map { item("id-$it", title = "标题$it", summary = "摘要$it") }
    val first = distributeArticleCards(items).map { column -> column.map { it.item.id } }
    val second = distributeArticleCards(items).map { column -> column.map { it.item.id } }
    assertEquals(first, second)
  }

  @Test
  fun emptyShelfProducesTwoEmptyColumns() {
    val columns = distributeArticleCards(emptyList())
    assertEquals(2, columns.size)
    assertTrue(columns.all { it.isEmpty() })
  }

  @Test
  fun heightEstimateGrowsWithLongerText() {
    val short = estimateArticleHeight(item("a", title = "短", summary = "短"))
    val long = estimateArticleHeight(item("a", title = "很长".repeat(20), summary = "很长".repeat(80)))
    assertTrue(long > short, "longer copy must estimate taller: $long vs $short")
  }

  @Test
  fun heightEstimateFallsBackToFullTextWhenSummaryIsBlank() {
    val withText = estimateArticleHeight(item("a", fullText = "正文".repeat(80)))
    val withoutText = estimateArticleHeight(item("a"))
    assertTrue(withText > withoutText)
  }

  @Test
  fun heightEstimateClampsTheTextContribution() {
    // Both are well past the caps (2.2 for the title, 3.6 for the body), so a longer one must not
    // outrank them: without the clamp an 18x-longer item would estimate ~7x taller.
    val long = estimateArticleHeight(item("a", title = "长".repeat(18 * 12), summary = "长".repeat(72 * 12)))
    val longer =
      estimateArticleHeight(item("a", title = "长".repeat(18 * 40), summary = "长".repeat(72 * 40)))
    assertEquals(long, longer, 0.0001f)
  }

  @Test
  fun heightEstimateOfATextOnlyItemIsOnePlusOnePlusOne() {
    // Exactly at both caps: 1f base + 2.2 title + 3.6 body would need far longer text, so the
    // reachable value for a single title-length and single body-length is the 3f floor + 2f.
    val exact = estimateArticleHeight(item("a", title = "长".repeat(18), summary = "长".repeat(72)))
    assertEquals(3f, exact, 0.0001f)
  }

  @Test
  fun aspectRatioIsStableForTheSameId() {
    val first = articleImageAspectRatio(item("stable"))
    repeat(5) { assertEquals(first, articleImageAspectRatio(item("stable"))) }
  }

  @Test
  fun aspectRatioStaysWithinTheDocumentedRange() {
    val ratios = (1..200).map { articleImageAspectRatio(item("id-$it")) }
    assertTrue(ratios.all { it >= 0.78f && it <= 1.34f }, "unexpected ratio: $ratios")
    assertTrue(ratios.toSet().size >= 4, "expected several distinct ratios, got ${ratios.toSet()}")
  }

  @Test
  fun tagsComeFromTheLabelledLineWhenPresent() {
    val tags = articleTags(item("a", fullText = "正文第一行\n标签：要闻·人物·趋势·资料\n正文"))
    assertEquals(listOf("要闻", "人物", "趋势"), tags)
  }

  @Test
  fun tagsAreSplitOnEveryDocumentedSeparator() {
    assertEquals(listOf("要闻", "人物"), articleTags(item("a", fullText = "标签：要闻,人物")))
    assertEquals(listOf("要闻", "人物"), articleTags(item("a", fullText = "标签：要闻，人物")))
    assertEquals(listOf("要闻", "人物"), articleTags(item("a", fullText = "标签：要闻、人物")))
  }

  @Test
  fun blankLabelLineFallsBackToTheFormatTag() {
    val tags = articleTags(item("a", fullText = "标签：   ", documentFormat = DocumentFormat.PDF))
    assertEquals(DocumentFormat.PDF.label, tags.first())
  }

  @Test
  fun fallbackTagsStartWithTheFormatThenTheContentType() {
    val document = articleTags(item("a", contentType = ContentType.DOCUMENT))
    assertEquals(ContentType.DOCUMENT.label, document.first())

    val web = articleTags(item("b"))
    assertEquals(ContentType.WEB_ARTICLE.label, web.first())
  }

  @Test
  fun tagsNeverExceedThreeAndNeverRepeat() {
    val tags = articleTags(item("a", fullText = "标签：要闻·人物·趋势·资料·待阅"))
    assertEquals(3, tags.size)
    assertEquals(tags.distinct(), tags)
  }

  @Test
  fun imageItemsEstimateTallerThanTextOnlyOnes() {
    val textOnly = estimateArticleHeight(item("a"))
    val withImage = estimateArticleHeight(item("a", imageResName = "home_search_tile"))
    assertTrue(withImage > textOnly)
  }
}
