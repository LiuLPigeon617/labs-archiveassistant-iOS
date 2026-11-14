package com.lyihub.archiveassistant.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyihub.archiveassistant.domain.ContentType
import com.lyihub.archiveassistant.domain.DocumentFormat
import com.lyihub.archiveassistant.domain.KnowledgeItem
import com.lyihub.archiveassistant.domain.Topic
import com.lyihub.archiveassistant.ui.archivePainter
import com.lyihub.archiveassistant.ui.archivePainterOrPlaceholder
import com.lyihub.archiveassistant.ui.components.ArchiveChip
import com.lyihub.archiveassistant.ui.components.PaneContainer
import com.lyihub.archiveassistant.ui.components.XuanPaperBackground
import com.lyihub.archiveassistant.ui.theme.ImperialCinnabar
import com.lyihub.archiveassistant.ui.theme.ImperialParchment
import com.lyihub.archiveassistant.ui.theme.LocalImperialFonts
import com.lyihub.archiveassistant.util.toChineseCount

/**
 * The detail pane: one ministry's shelf of items, as a two-column memorial wall.
 *
 * Ported from `app/src/main/java/com/lyihub/archiveassistant/ui/screens/DetailPane.kt` (its first
 * half; the dialogs stay behind until the picker and the image decoder are shared — see the note at
 * the bottom of this file).
 *
 * Five platform anchors had to move, and each has a shared replacement:
 *
 *  - `R.drawable.home_search_tile` / `memorial_xuan_paper` / `memorial_cover_corner` /
 *    `imperial_ornament_pattern` became [archivePainterOrPlaceholder] names. All four are already in
 *    `bundledDrawable`, so this pane draws the same art as Android rather than placeholder squares.
 *  - `Icons.Default.Add` became [AddGlyph], drawn locally: this module does not depend on
 *    `material-icons`, the same reason HomePane strokes its own gear.
 *  - `ImperialDisplayFont` became `LocalImperialFonts.current.display`.
 *  - `title.lineSequence()` became `title.lines()` — `lineSequence` is a JVM-only stdlib extension and
 *    is the exact call that fails to resolve on Kotlin/Native.
 *  - `BitmapFactory.Options(inJustDecodeBounds = true)` measured a bundled image so a portrait photo
 *    could be cropped to a minimum ratio. There is no `R`-lookup here, and
 *    [com.lyihub.archiveassistant.platform.ImageBytesDecoder] needs encoded bytes rather than a
 *    drawable, so [articleImageLayout] reads the painter's intrinsic size instead — the same number,
 *    because the resource loader already decoded that asset to build the painter. Only when the
 *    asset is unbundled (1×1 placeholder) does the card fall back to [articleImageAspectRatio], the
 *    estimate the masonry heights use.
 */

private val DetailBorder = Color.Black
private val DetailPaperDeep = ImperialParchment
private val DetailInk = Color.Black
private val DetailCinnabar = ImperialCinnabar
private val DetailCardCorner = 9.dp
private val DetailTagChipShape = ArchiveFlatCutShape

@Composable
fun DetailPane(
  topic: Topic,
  items: List<KnowledgeItem>,
  searchQuery: String,
  onBack: () -> Unit,
  onItemClick: (String) -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean = true,
) {
  val horizontalPadding = 24.dp
  val topPadding = 56.dp
  val headerOverlayHeight = if (items.isEmpty()) 100.dp else 124.dp
  val listState = rememberLazyListState()
  val density = LocalDensity.current
  val headerFadeDistancePx = with(density) { 58.dp.toPx() }
  val headerAlpha =
    if (listState.firstVisibleItemIndex > 0) {
      0f
    } else {
      (1f - listState.firstVisibleItemScrollOffset / headerFadeDistancePx).coerceIn(0f, 1f)
    }
  val availableTags = remember(items) { items.flatMap(::articleTags).distinct() }
  var activeTags by remember(availableTags) { mutableStateOf(availableTags.toSet()) }
  val filteredItems =
    remember(items, activeTags) {
      if (availableTags.isEmpty() || activeTags.isEmpty()) {
        emptyList()
      } else {
        items.filter { item -> articleTags(item).any { tag -> tag in activeTags } }
      }
    }

  PaneContainer(modifier = modifier.testTag("detail-pane")) {
    Box(modifier = Modifier.fillMaxSize().weight(1f)) {
      LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        overscrollEffect = null,
        contentPadding =
          PaddingValues(
            start = horizontalPadding,
            top = topPadding + headerOverlayHeight,
            end = horizontalPadding,
            bottom = 28.dp,
          ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
      ) {
        if (items.isEmpty()) {
          item { EmptyMemorialShelf(modifier = Modifier.fillMaxWidth()) }
        } else {
          item {
            if (filteredItems.isEmpty()) {
              EmptyFilteredShelf(modifier = Modifier.fillMaxWidth())
            } else {
              ArticleMasonryGrid(
                items = filteredItems,
                searchQuery = searchQuery,
                onItemClick = onItemClick,
                modifier = Modifier.fillMaxWidth(),
              )
            }
          }
        }
      }
      if (headerAlpha > 0.02f) {
        Column(
          modifier =
            Modifier.align(Alignment.TopStart)
              .padding(start = horizontalPadding, top = topPadding, end = horizontalPadding)
              .fillMaxWidth()
              .graphicsLayer { alpha = headerAlpha },
          verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          DetailCourtHeader(
            topic = topic,
            itemCount = items.size,
            onBack = onBack,
            modifier = Modifier.fillMaxWidth(),
            showBackButton = showBackButton,
          )
          if (items.isNotEmpty()) {
            ArticleFilterBar(
              tags = availableTags,
              activeTags = activeTags,
              onToggleTag = { tag ->
                activeTags =
                  if (tag in activeTags) {
                    activeTags - tag
                  } else {
                    activeTags + tag
                  }
              },
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ArticleFilterBar(
  tags: List<String>,
  activeTags: Set<String>,
  onToggleTag: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier,
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = "筛选：",
      style = MaterialTheme.typography.labelLarge,
      color = DetailInk,
      maxLines = 1,
    )
    LazyRow(
      modifier = Modifier.weight(1f),
      overscrollEffect = null,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      items(tags) { tag ->
        ArticleTagChip(
          text = tag,
          selected = tag in activeTags,
          fixedColor = tagAccentColor(tag),
          onClick = { onToggleTag(tag) },
        )
      }
    }
  }
}

@Composable
private fun EmptyFilteredShelf(modifier: Modifier = Modifier) {
  Box(
    modifier =
      modifier
        .height(92.dp)
        .clip(RoundedCornerShape(DetailCardCorner))
        .background(Color.White.copy(alpha = 0.38f)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "请选择至少一个标签",
      style = MaterialTheme.typography.bodyMedium,
      color = DetailInk.copy(alpha = 0.62f),
    )
  }
}

@Composable
private fun ArticleMasonryGrid(
  items: List<KnowledgeItem>,
  searchQuery: String,
  onItemClick: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val columns = distributeArticleCards(items)
  Row(
    modifier = modifier,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    repeat(2) { column ->
      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        columns[column].forEach { card ->
          MemorialArticleCard(
            item = card.item,
            searchQuery = searchQuery,
            onClick = { onItemClick(card.item.id) },
            modifier = Modifier.fillMaxWidth(),
          )
        }
      }
    }
  }
}

internal data class MasonryArticleCard(val item: KnowledgeItem)

/**
 * Splits the shelf into two columns by always appending to the currently shorter one.
 *
 * Deterministic on purpose: a keystroke in the search box re-renders this, and a random or
 * measurement-driven split makes cards jump between columns mid-typing.
 */
internal fun distributeArticleCards(items: List<KnowledgeItem>): List<List<MasonryArticleCard>> {
  val columns = List(2) { mutableListOf<MasonryArticleCard>() }
  val heights = FloatArray(2)
  items.forEach { item ->
    val column = if (heights[0] <= heights[1]) 0 else 1
    columns[column] += MasonryArticleCard(item)
    heights[column] += estimateArticleHeight(item)
  }
  return columns
}

/**
 * A text-and-image height estimate, in arbitrary units — only the ordering and the rough magnitude
 * matter, because the result is used to choose a column, not to lay anything out.
 */
internal fun estimateArticleHeight(item: KnowledgeItem): Float {
  val textWeight =
    1f +
      (item.title.length / 18f).coerceAtMost(2.2f) +
      (item.summary.ifBlank { item.fullText }.length / 72f).coerceAtMost(3.6f)
  val imageWeight =
    if (item.imageResName != null) 1.45f / articleImageAspectRatio(item).coerceAtLeast(0.58f) else 0f
  return textWeight + imageWeight
}

/**
 * A stable pseudo-random aspect ratio per item id.
 *
 * Items carry no intrinsic image size on this side of the platform boundary, so the wall fakes the
 * variation a real photo set would have. Keyed on the id rather than the index so a card keeps its
 * shape when the filter above it changes.
 */
internal fun articleImageAspectRatio(item: KnowledgeItem): Float {
  return when (positiveMod(item.id.hashCode(), 5)) {
    0 -> 1.18f
    1 -> 0.92f
    2 -> 1.34f
    3 -> 0.78f
    else -> 1.05f
  }
}

/**
 * The intrinsic shape of a bundled card image, or null when it cannot be measured.
 *
 * The Android original opened the drawable through `BitmapFactory.Options(inJustDecodeBounds = true)`
 * to learn the pixel size without paying for a decode. This module cannot reach `R` or a resource
 * `InputStream`, so it renders the painter and reads the intrinsic size the resource loader already
 * decoded — same number for a bundled asset, no second decode at the call site.
 *
 * Returns null for the placeholder painter (a 1×1 transparent bitmap), which is exactly the case
 * where the estimate in [articleImageAspectRatio] is preferable to "1:1, crop".
 */
@Composable
private fun articleImageLayout(item: KnowledgeItem): ArticleImageLayout? {
  val assetName = item.imageResName ?: return null
  val painter = archivePainter(assetName) ?: return null
  val size = painter.intrinsicSize
  if (size.width <= 1f || size.height <= 1f) return null
  val originalRatio = size.width / size.height
  val minRatio = 9f / 16f
  return ArticleImageLayout(
    aspectRatio = originalRatio.coerceAtLeast(minRatio),
    cropToMaxHeight = originalRatio < minRatio,
  )
}

private data class ArticleImageLayout(
  val aspectRatio: Float,
  val cropToMaxHeight: Boolean,
)

@Composable
private fun DetailCourtHeader(
  topic: Topic,
  itemCount: Int,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  showBackButton: Boolean,
) {
  PaneHeroHeader(
    title = topic.title,
    description = "${itemCount.toChineseCount()}篇 · ${folderDescription(topic)}",
    showBackButton = showBackButton,
    backIcon = { BackArrowGlyph(tint = DetailInk, modifier = Modifier.size(20.dp)) },
    onBack = onBack,
    modifier = modifier.testTag("detail-summary"),
  )
}

private fun folderDescription(topic: Topic): String =
  folderVisualForTopicId(topic.id).description

@Composable
private fun MemorialArticleCard(
  item: KnowledgeItem,
  searchQuery: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val cardShape = ArchiveFlatCutShape
  val imageShape = ArchiveFlatCutShape
  val tags = articleTags(item)
  val imageLayout = articleImageLayout(item)
  Box(
    modifier =
      modifier
        .shadow(8.dp, cardShape, clip = false)
        .clip(cardShape)
        .background(DetailPaperDeep, cardShape)
        .clickable(onClick = onClick)
        .testTag("knowledge-card-${item.id}"),
  ) {
    Image(
      painter = archivePainterOrPlaceholder("home_search_tile"),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
    )
    Box(modifier = Modifier.matchParentSize().background(Color.White.copy(alpha = 0.2f)))
    Column(modifier = Modifier.fillMaxWidth()) {
      if (item.imageResName != null) {
        Box(modifier = Modifier.fillMaxWidth().clip(imageShape)) {
          Image(
            painter = archivePainterOrPlaceholder(item.imageResName.orEmpty()),
            contentDescription = null,
            modifier =
              Modifier.fillMaxWidth()
                .aspectRatio(imageLayout?.aspectRatio ?: articleImageAspectRatio(item))
                .clip(imageShape),
            contentScale =
              if (imageLayout?.cropToMaxHeight == true) ContentScale.Crop else ContentScale.Fit,
          )
        }
      }
      Column(
        modifier =
          Modifier.fillMaxWidth()
            .padding(
              start = 7.dp,
              top = if (item.imageResName != null) 6.dp else 8.dp,
              end = 7.dp,
              bottom = 8.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(7.dp),
      ) {
        Text(
          text =
            buildHighlightedText(
              text = item.title,
              query = searchQuery,
              highlightColor = DetailInk,
              highlightBgColor = Color(0xFFF2D88A).copy(alpha = 0.84f),
            ),
          style = MaterialTheme.typography.titleSmall,
          color = DetailInk,
          fontWeight = FontWeight.Normal,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(top = 3.dp, bottom = 2.dp),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          tags.forEach { tag ->
            ArticleTagChip(
              text = tag,
              selected = true,
              fixedColor = tagAccentColor(tag),
              onClick = null,
            )
          }
        }
      }
    }
  }
}

/**
 * The tags a card shows.
 *
 * A hand-written "标签：" line in the body wins, because that is how the seeded samples describe
 * themselves; everything else falls back to "its format plus two stable pseudo-tags", which keeps the
 * wall visually even without inventing metadata the model does not have.
 */
internal fun articleTags(item: KnowledgeItem): List<String> {
  item.fullText
    .lines()
    .firstOrNull { it.startsWith("标签：") }
    ?.removePrefix("标签：")
    ?.split("·", "、", ",", "，")
    ?.map { it.trim() }
    ?.filter { it.isNotEmpty() }
    ?.distinct()
    ?.take(3)
    ?.takeIf { it.isNotEmpty() }
    ?.let {
      return it
    }

  val seed = (item.title.length + item.summary.length + item.id.length).coerceAtLeast(0)
  return listOf(item.documentFormat?.label ?: item.contentType.label)
    .plus(fallbackTagLabels(seed))
    .distinct()
    .take(3)
}

@Composable
private fun ArticleTagChip(
  text: String,
  modifier: Modifier = Modifier,
  selected: Boolean,
  fixedColor: Color,
  onClick: (() -> Unit)?,
) {
  ArchiveChip(
    label = text,
    selected = selected,
    accentColor = fixedColor,
    modifier = modifier,
    shape = DetailTagChipShape,
    textStyle =
      MaterialTheme.typography.labelSmall.copy(
        fontFamily = LocalImperialFonts.current.display,
        fontSize = 10.sp,
        lineHeight = 10.sp,
      ),
    onClick = onClick,
  )
}

@Composable
private fun EmptyMemorialShelf(modifier: Modifier = Modifier) {
  Box(
    modifier = modifier.background(DetailPaperDeep).border(1.dp, DetailBorder.copy(alpha = 0.78f)),
    contentAlignment = Alignment.Center,
  ) {
    Image(
      painter = archivePainterOrPlaceholder("memorial_xuan_paper"),
      contentDescription = null,
      modifier = Modifier.matchParentSize(),
      contentScale = ContentScale.Crop,
      alpha = 0.68f,
    )
    XuanPaperBackground(
      modifier = Modifier.matchParentSize(),
      textureAlpha = 0.68f,
      veilAlpha = 0.9f,
    ) {}
    Column(
      modifier = Modifier.padding(36.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      AddGlyph(tint = DetailCinnabar.copy(alpha = 0.76f), modifier = Modifier.size(24.dp))
      Text(
        text = "此档暂无奏折",
        style = MaterialTheme.typography.headlineSmall,
        color = DetailInk,
        fontWeight = FontWeight.Normal,
      )
      Text(
        text = "可由中书录入，门下筛选后归入尚书档案。",
        style = MaterialTheme.typography.bodyMedium,
        color = DetailInk.copy(alpha = 0.68f),
      )
    }
  }
}

@Composable
private fun DecorativeCorner(
  modifier: Modifier = Modifier,
  rotation: Float,
) {
  Image(
    painter = archivePainterOrPlaceholder("memorial_cover_corner"),
    contentDescription = null,
    modifier = modifier.padding(8.dp).size(30.dp).graphicsLayer(rotationZ = rotation),
    contentScale = ContentScale.Fit,
    alpha = 0.58f,
    colorFilter = ColorFilter.tint(DetailCinnabar.copy(alpha = 0.76f)),
  )
}

@Composable
private fun DecorativeDetailImage(
  modifier: Modifier = Modifier,
  alpha: Float,
  tint: Color,
) {
  Image(
    painter = archivePainterOrPlaceholder("imperial_ornament_pattern"),
    contentDescription = null,
    modifier = modifier,
    contentScale = ContentScale.Crop,
    alpha = alpha,
    colorFilter = ColorFilter.tint(tint),
  )
}

/** The back affordance, stroked locally: this module has no `material-icons`. */
@Composable
private fun BackArrowGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.semantics { contentDescription = "返回" }) {
    val centerY = size.height / 2f
    val strokeWidth = size.height * 0.11f
    val head = size.height * 0.26f
    drawLine(
      color = tint,
      start = Offset(size.width * 0.92f, centerY),
      end = Offset(size.width * 0.12f, centerY),
      strokeWidth = strokeWidth,
    )
    drawPath(
      path =
        Path().apply {
          moveTo(size.width * 0.44f, centerY - head)
          lineTo(size.width * 0.12f, centerY)
          lineTo(size.width * 0.44f, centerY + head)
        },
      color = tint,
      style = Stroke(width = strokeWidth),
    )
  }
}

/** Stands in for `Icons.Default.Add` on the empty shelf. */
@Composable
private fun AddGlyph(tint: Color, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier.semantics { contentDescription = "新增" }) {
    val strokeWidth = size.minDimension * 0.08f
    drawLine(
      color = tint,
      start = Offset(size.width / 2f, size.height * 0.16f),
      end = Offset(size.width / 2f, size.height * 0.84f),
      strokeWidth = strokeWidth,
    )
    drawLine(
      color = tint,
      start = Offset(size.width * 0.16f, size.height / 2f),
      end = Offset(size.width * 0.84f, size.height / 2f),
      strokeWidth = strokeWidth,
    )
  }
}

/*
 * Still in `:app`: AddItemDialog, ClipboardDialog and DeleteItemConfirmDialog.
 *
 * The first two need two platform halves that do not exist on this side yet — a system file picker
 * that returns a readable source, and a decoder that turns imported bytes into an `ImageBitmap` for
 * the preview. `IosNativeBridge.pickFile` is already installed by Swift, so the picker is a small
 * `expect` away; the decoder is the real gap. DeleteItemConfirmDialog is pure copy and can be dragged
 * along whenever the other two move, to keep the trio together.
 */
