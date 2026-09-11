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

internal val MemorialCoverAssets =
  listOf(
    "memorial_cover_pattern",
    "memorial_cover_02",
    "memorial_cover_03",
    "memorial_cover_04",
    "memorial_cover_05",
    "memorial_cover_06",
    "memorial_cover_07",
    "memorial_cover_08",
    "memorial_cover_09",
    "memorial_cover_10",
    "memorial_cover_11",
    "memorial_cover_12",
    "memorial_cover_13",
    "memorial_cover_14",
    "memorial_cover_15",
    "memorial_cover_16",
    "memorial_cover_17",
    "memorial_cover_18",
    "memorial_cover_19",
    "memorial_cover_20",
    "memorial_cover_21",
    "memorial_cover_22",
    "memorial_cover_23",
  )

internal fun folderVisual(index: Int): FolderVisual = FolderVisuals[index % FolderVisuals.size]
