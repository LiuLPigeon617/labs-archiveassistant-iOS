package com.lyihub.archiveassistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lyihub.archiveassistant.ui.archiveFontFamily

// Font asset names, free of the Android R.font.* integer ids that do not exist outside :app.
internal const val TITLE_FONT_ASSET = "san_ji_xing_kai_jian_ti_cu"
internal const val DISPLAY_FONT_ASSET = "dinglie_song_typeface"

/**
 * Resolves the imperial display fonts for the current platform.
 *
 * Falls back to the platform serif family when the bundled fonts are unavailable, which keeps the
 * theme valid on targets where the assets have not been shipped yet (currently iOS and the desktop
 * verification target).
 */
@Composable
fun rememberImperialFonts(): ImperialFonts {
  val title = archiveFontFamily(TITLE_FONT_ASSET)
  val display = archiveFontFamily(DISPLAY_FONT_ASSET)
  return remember(title, display) {
    ImperialFonts(
      title = title ?: FontFamily.Serif,
      display = display ?: FontFamily.Serif,
    )
  }
}

data class ImperialFonts(
  val title: FontFamily,
  val display: FontFamily,
)

/**
 * Typography using the resolved imperial fonts.
 *
 * The original was a top-level `val`, which is impossible now that font loading is @Composable.
 * Callers wrap content in [ProvideImperialTypography] instead.
 */
@Composable
fun imperialTypography(fonts: ImperialFonts): Typography =
  Typography(
    displayLarge =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 57.sp,
        lineHeight = 64.sp,
      ),
    displayMedium =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 45.sp,
        lineHeight = 52.sp,
      ),
    displaySmall =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 44.sp,
      ),
    headlineLarge =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 40.sp,
      ),
    headlineMedium =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 36.sp,
      ),
    headlineSmall =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 32.sp,
      ),
    titleLarge =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
      ),
    titleMedium =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    titleSmall =
      TextStyle(
        fontFamily = fonts.title,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodyLarge =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
      ),
    bodyMedium =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    bodySmall =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelLarge =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
      ),
    labelMedium =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
      ),
    labelSmall =
      TextStyle(
        fontFamily = fonts.display,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
      ),
  )

/** Applies the imperial typography to [content]. */
@Composable
fun ProvideImperialTypography(content: @Composable () -> Unit) {
  val fonts = rememberImperialFonts()
  val typography = imperialTypography(fonts)
  CompositionLocalProvider(
    // MaterialTheme reads LocalTypography, so overriding it here reaches every descendant.
    androidx.compose.material3.LocalTextStyle provides TextStyle.Default,
  ) {
    androidx.compose.material3.MaterialTheme(
      typography = typography,
      content = content,
    )
  }
}
