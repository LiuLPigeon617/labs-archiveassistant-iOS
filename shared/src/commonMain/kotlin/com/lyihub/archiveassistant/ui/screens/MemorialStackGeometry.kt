package com.lyihub.archiveassistant.ui.screens
import androidx.compose.ui.util.lerp

internal data class MemorialStackPose(
  val offsetX: Float,
  val offsetY: Float,
  val scale: Float,
  val rotation: Float,
)

internal object MemorialStackGeometry {
  fun pose(
    level: Int,
    shiftProgress: Float,
    dp: (Float) -> Float,
  ): MemorialStackPose {
    val nextLevel = (level - 1).coerceAtLeast(0)
    val progress = shiftProgress.coerceIn(0f, 1f)
    return MemorialStackPose(
      offsetX = lerp(offsetX(level, dp), offsetX(nextLevel, dp), progress),
      offsetY = lerp(offsetY(level, dp), offsetY(nextLevel, dp), progress),
      scale = lerp(scale(level), scale(nextLevel), progress),
      rotation = lerp(rotation(level), rotation(nextLevel), progress),
    )
  }

  fun offsetX(level: Int, dp: (Float) -> Float): Float {
    return when (level) {
      0 -> 0f
      1 -> dp(8f)
      2 -> -dp(7f)
      3 -> dp(13f)
      4 -> -dp(11f)
      else -> dp(5f)
    }
  }

  fun offsetY(level: Int, dp: (Float) -> Float): Float {
    if (level <= 0) return 0f
    return dp(7f + level * 6.5f)
  }

  fun scale(level: Int): Float {
    if (level <= 0) return 1f
    return 1f - level * 0.014f
  }

  fun rotation(level: Int): Float {
    return when (level) {
      0 -> 0f
      1 -> -1.1f
      2 -> 1.35f
      3 -> -0.8f
      4 -> 1.05f
      else -> -0.55f
    }
  }
}
