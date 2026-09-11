package com.lyihub.archiveassistant.ui.screens

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

private val TagBaseLabels = listOf("要闻", "人物", "趋势", "资料", "待阅", "摘录", "案例", "长文")

internal val TagAccentColors =
  listOf(
    Color(0xFFB83E2F),
    Color(0xFF8B654A),
    Color(0xFFD1A36B),
    Color(0xFFE65D3F),
    Color(0xFF9C4A37),
    Color(0xFF3E3E46),
    Color(0xFF78ABCC),
    Color(0xFF6F8D72),
  )

internal fun tagAccentColor(tag: String): Color {
  val index =
    TagBaseLabels.indexOf(tag).takeIf { it >= 0 }
      ?: positiveMod(tag.hashCode(), TagAccentColors.size)
  return TagAccentColors[index % TagAccentColors.size]
}

internal fun tagAccentArgb(tag: String): Int = tagAccentColor(tag).toArgb()

internal fun fallbackTagLabels(seed: Int): List<String> {
  val first = TagBaseLabels[seed % TagBaseLabels.size]
  val second = TagBaseLabels[(seed + 3) % TagBaseLabels.size]
  return listOf(first, second)
}

internal fun positiveMod(value: Int, modulus: Int): Int = ((value % modulus) + modulus) % modulus
