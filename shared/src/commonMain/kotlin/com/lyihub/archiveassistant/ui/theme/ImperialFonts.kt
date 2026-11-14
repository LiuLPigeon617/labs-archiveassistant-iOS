package com.lyihub.archiveassistant.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.font.FontFamily

/**
 * Imperial fonts for the current platform.
 *
 * Provided through a CompositionLocal rather than threading parameters through every private helper
 * composable in the panes, which would otherwise each need a font argument.
 */
data class ImperialFonts(
  val title: FontFamily,
  val display: FontFamily,
)

val LocalImperialFonts =
  staticCompositionLocalOf { ImperialFonts(title = FontFamily.Serif, display = FontFamily.Serif) }

/** Provides [fonts] to all descendants. */
@Composable
fun ProvideImperialFonts(fonts: ImperialFonts, content: @Composable () -> Unit) {
  CompositionLocalProvider(LocalImperialFonts provides fonts, content = content)
}
