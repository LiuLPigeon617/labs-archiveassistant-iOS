package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.domain.SixMinistry

internal data class FolderVisual(
  val description: String,
  val imageAsset: String,
)

internal data class ArchiveTileVisual(
  val backgroundAsset: String,
  val borderColor: Color,
)

internal const val ArchiveCutCornerNotchDp = 8

/**
 * How many memorials the home screen advertises as awaiting review.
 *
 * Ported from `app/src/main/java/com/lyihub/archiveassistant/ui/screens/MemorialDemoModels.kt:87`,
 * where it sat next to the Android-only colour constants. It lives here because this file is already
 * the home of the shared panes' sample data ([SampleTopicIds], [SampleTopicTitles]) and
 * [TOTAL_PENDING_MEMORIALS] is the same kind of fixture: the memorial demo has no real backlog yet,
 * so the count is a constant rather than derived from state.
 */
internal const val TOTAL_PENDING_MEMORIALS = 6
internal val ArchiveCutCornerShape: Shape = FixedCutCornerShape(ArchiveCutCornerNotchDp)
internal val ArchiveFlatCutShape: Shape = FlatCutCornerShape(ArchiveCutCornerNotchDp)

private class FixedCutCornerShape(private val notchDp: Int) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val notch = with(density) { notchDp.dp.toPx() }.coerceAtMost(size.minDimension * 0.28f)
    val path =
      Path().apply {
        moveTo(notch, 0f)
        lineTo(size.width - notch, 0f)
        quadraticTo(size.width - notch, notch, size.width, notch)
        lineTo(size.width, size.height - notch)
        quadraticTo(size.width - notch, size.height - notch, size.width - notch, size.height)
        lineTo(notch, size.height)
        quadraticTo(notch, size.height - notch, 0f, size.height - notch)
        lineTo(0f, notch)
        quadraticTo(notch, notch, notch, 0f)
        close()
      }
    return Outline.Generic(path)
  }
}

private class FlatCutCornerShape(private val notchDp: Int) : Shape {
  override fun createOutline(
    size: Size,
    layoutDirection: LayoutDirection,
    density: Density,
  ): Outline {
    val notch = with(density) { notchDp.dp.toPx() }.coerceAtMost(size.minDimension * 0.28f)
    val path =
      Path().apply {
        moveTo(notch, 0f)
        lineTo(size.width - notch, 0f)
        lineTo(size.width, notch)
        lineTo(size.width, size.height - notch)
        lineTo(size.width - notch, size.height)
        lineTo(notch, size.height)
        lineTo(0f, size.height - notch)
        lineTo(0f, notch)
        close()
      }
    return Outline.Generic(path)
  }
}

internal val ZhongshuTileVisual =
  ArchiveTileVisual(
    backgroundAsset = "home_zhongshu_tile",
    borderColor = Color(0xFF9C4A37),
  )

internal val MenxiaTileVisual =
  ArchiveTileVisual(
    backgroundAsset = "home_menxia_tile",
    borderColor = Color(0xFFC6A06B),
  )

internal val MemorialTileVisual =
  ArchiveTileVisual(
    backgroundAsset = "home_memorial_tile",
    borderColor = Color(0xFFAFD9BD),
  )

internal val ClipboardTileVisual =
  ArchiveTileVisual(
    backgroundAsset = "home_clipboard_tile",
    borderColor = Color(0xFF78ABCC),
  )

internal val SearchTileVisual =
  ArchiveTileVisual(
    backgroundAsset = "home_search_new_tile",
    borderColor = Color(0xFF3E3E46),
  )

internal val FolderVisuals =
  listOf(
    FolderVisual(
      description = "近期收藏与重点资料归档",
      imageAsset = "tsieina_department_pattern_9617",
    ),
    FolderVisual(
      description = "按主题收束同类资料",
      imageAsset = "tsieina_department_pattern_10412",
    ),
    FolderVisual(
      description = "保留可复查的摘录与来源",
      imageAsset = "tsieina_department_pattern_10059",
    ),
    FolderVisual(
      description = "聚合技术、工具与实现线索",
      imageAsset = "tsieina_department_pattern_9611",
    ),
    FolderVisual(
      description = "沉淀判断、风险与待复核内容",
      imageAsset = "tsieina_department_pattern_9945",
    ),
    FolderVisual(
      description = "整理产品、设计与制作材料",
      imageAsset = "tsieina_department_pattern_9610",
    ),
  )

internal val SampleTopicIds =
  listOf(
    SixMinistry.OFFICIALS.id,
    SixMinistry.TREASURY.id,
    SixMinistry.RITES.id,
    SixMinistry.MILITARY.id,
    SixMinistry.JUSTICE.id,
    SixMinistry.WORKS.id,
  )

internal val SampleTopicTitles =
  listOf(
    SixMinistry.OFFICIALS.label,
    SixMinistry.TREASURY.label,
    SixMinistry.RITES.label,
    SixMinistry.MILITARY.label,
    SixMinistry.JUSTICE.label,
    SixMinistry.WORKS.label,
  )

internal fun folderIndexForTopicId(topicId: String): Int {
  return SampleTopicIds.indexOf(topicId).takeIf { it >= 0 } ?: 0
}

internal fun folderVisualForTopicId(topicId: String): FolderVisual {
  return folderVisual(folderIndexForTopicId(topicId))
}

internal fun folderTitleForTopicId(topicId: String): String {
  return SampleTopicTitles.getOrElse(folderIndexForTopicId(topicId)) { "尚书省" }
}

/**
 * The bundled memorial cover pool, in the order the wheel walks it.
 *
 * Only the first batch is bundled: the paper pattern plus covers `02`–`06`. That is the smallest
 * set where the six demo memorials each get a distinct cover, so the briefing wheel and the reading
 * view look finished rather than "asset pack not done". Covers `07`–`23` stay in `:app` on purpose —
 * they are the bulk of the artwork bytes, and the fold view that decides the final display aspect
 * ratio has not been ported yet, so cropping them now would freeze a ratio that is about to change.
 *
 * Keep this list in sync with `ArchivePainter.bundledDrawable`: a name listed here but not bundled
 * resolves to `null` and draws a 1×1 transparent placeholder, which no screenshot check can catch.
 */
internal val MemorialCoverAssets =
  listOf(
    "memorial_cover_pattern",
    "memorial_cover_02",
    "memorial_cover_03",
    "memorial_cover_04",
    "memorial_cover_05",
    "memorial_cover_06",
  )

internal fun folderVisual(index: Int): FolderVisual = FolderVisuals[index % FolderVisuals.size]
