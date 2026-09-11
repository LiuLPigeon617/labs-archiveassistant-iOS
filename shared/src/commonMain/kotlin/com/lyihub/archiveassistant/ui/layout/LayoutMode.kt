package com.lyihub.archiveassistant.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Stable
enum class LayoutMode {
  COMPACT,
  EXPANDED,
  FOLDABLE,
}

@Stable
data class WindowLayoutInfo(
  val mode: LayoutMode,
  val widthDp: Dp,
  val hingeBounds: List<HingeBounds> = emptyList(),
)

@Stable
data class HingeBounds(
  val left: Int,
  val top: Int,
  val right: Int,
  val bottom: Int,
)

object LayoutModeDefaults {
  val COMPACT_MAX_WIDTH = 600.dp
  val EXPANDED_MIN_WIDTH = 840.dp
}

@Composable
fun rememberWindowLayoutInfo(hingeBounds: List<HingeBounds> = emptyList()): WindowLayoutInfo {
  val windowInfo = LocalWindowInfo.current
  val density = LocalDensity.current.density
  val widthDp = (windowInfo.containerSize.width / density).dp

  val mode =
    when {
      hingeBounds.isNotEmpty() -> LayoutMode.FOLDABLE
      widthDp < LayoutModeDefaults.COMPACT_MAX_WIDTH -> LayoutMode.COMPACT
      widthDp >= LayoutModeDefaults.EXPANDED_MIN_WIDTH -> LayoutMode.EXPANDED
      else -> LayoutMode.EXPANDED
    }

  return WindowLayoutInfo(
    mode = mode,
    widthDp = widthDp,
    hingeBounds = hingeBounds,
  )
}

fun HingeBounds.intersectsHorizontal(y: Int, height: Int): Boolean {
  return y < this.bottom && (y + height) > this.top
}

fun HingeBounds.intersectsVertical(x: Int, width: Int): Boolean {
  return x < this.right && (x + width) > this.left
}

fun List<HingeBounds>.isSafeHorizontalArea(y: Int, height: Int): Boolean {
  return none { it.intersectsHorizontal(y, height) }
}

fun List<HingeBounds>.isSafeVerticalArea(x: Int, width: Int): Boolean {
  return none { it.intersectsVertical(x, width) }
}

fun WindowLayoutInfo.shouldShowTwoPanes(selectedTopicId: String?): Boolean {
  return when (mode) {
    LayoutMode.COMPACT -> false
    LayoutMode.EXPANDED -> true
    LayoutMode.FOLDABLE -> true
  }
}
