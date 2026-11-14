package com.lyihub.archiveassistant.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.lyihub.archiveassistant.ui.archiveFontFamily

/**
 * The imperial colour scheme, mapped onto the Material 3 slots the shared UI actually reads.
 *
 * Ported from `:app`'s `ui/theme/Theme.kt` so the shared module stops falling back to the Material 3
 * **baseline** scheme. Before this existed, `MaterialTheme.colorScheme.primary` resolved to the
 * default purple, which is why settings chrome did not match the imperial palette even though the
 * palette's own literals were already used elsewhere.
 *
 * Both variants are ported, not just light: the dark tokens were already defined next to the light
 * ones in `Color.kt`, and dropping them would have silently changed how this looks in dark mode
 * relative to the Android app. `darkTheme` follows the platform, matching `:app`.
 */
private val LightColorScheme =
  lightColorScheme(
    primary = Terracotta,
    onPrimary = Ivory,
    secondary = Coral,
    onSecondary = Ivory,
    tertiary = Muted,
    onTertiary = Ivory,
    background = Parchment,
    onBackground = DarkText,
    surface = Ivory,
    onSurface = DarkText,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = Muted,
    outline = WarmBorder,
    outlineVariant = WarmSurface,
    inverseSurface = DarkText,
    inverseOnSurface = Ivory,
    error = ImperialCinnabar,
    onError = Ivory,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = DarkTerracotta,
    onPrimary = ImperialIvory,
    secondary = DarkCoral,
    onSecondary = ImperialIvory,
    tertiary = DarkMuted,
    onTertiary = ImperialIvory,
    background = DarkParchment,
    onBackground = ImperialIvory,
    surface = DarkIvory,
    onSurface = ImperialUmber,
    surfaceVariant = DarkWarmSurfaceVariant,
    onSurfaceVariant = ImperialUmber,
    outline = DarkWarmBorder,
    outlineVariant = DarkWarmSurface,
    inverseSurface = ImperialIvory,
    inverseOnSurface = ImperialUmber,
    error = ImperialCinnabar,
    onError = ImperialIvory,
  )

/**
 * Applies the imperial palette and the calligraphic type scale to [content].
 *
 * Two Android-only behaviours of the `:app` version are deliberately **not** ported here, so nobody
 * has to guess later whether they were forgotten:
 *
 *  - `dynamicColor` (Material You) — there is no cross-platform equivalent, and its result would
 *    contradict the imperial brief.
 *  - Edge-to-edge system bar styling — a window concern, not a theming one, and it currently belongs
 *    to the Android host: `app/src/main/java/com/lyihub/archiveassistant/ui/theme/Theme.kt`, which
 *    still calls `enableEdgeToEdge`.
 *
 * Both light and dark resolve through this one entry point, so a caller never passes a scheme.
 */
@Composable
fun ArchiveAssistantTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = archiveTypography(),
    content = content,
  )
}

/**
 * The calligraphic type scale, built from the bundled fonts.
 *
 * Ported level-by-level from `:app`'s Type.kt rather than only the levels the shared UI currently
 * uses: a partial [Typography] would leave the untouched levels on the platform default face, which
 * would look like an accident rather than a decision.
 *
 * Built inline instead of remembered, because resolving a face goes through `archiveFontFamily`
 * (`@Composable`, since the Compose resource API loads fonts through composition) and a `remember`
 * lambda is not a composable context. The resource layer caches the font bytes, so rebuilding these
 * `TextStyle`s on recomposition is cheap.
 */
@Composable
private fun archiveTypography(): Typography {
  val title = archiveFontFamily("san_ji_xing_kai_jian_ti_cu") ?: FontFamily.Serif
  val display = archiveFontFamily("dinglie_song_typeface") ?: FontFamily.Serif
  return Typography(
    displayLarge = imperialStyle(title, 42, 50),
    displayMedium = imperialStyle(title, 34, 42),
    displaySmall = imperialStyle(title, 30, 38),
    headlineLarge = imperialStyle(title, 27, 35),
    headlineMedium = imperialStyle(title, 23, 31),
    headlineSmall = imperialStyle(title, 21, 29),
    titleLarge = imperialStyle(title, 21, 29),
    titleMedium = imperialStyle(title, 18, 26),
    titleSmall = imperialStyle(title, 16, 22),
    bodyLarge = imperialStyle(display, 16, 24),
    bodyMedium = imperialStyle(display, 14, 20),
    bodySmall = imperialStyle(display, 12, 16),
    labelLarge = imperialStyle(display, 14, 20),
    labelMedium = imperialStyle(display, 12, 16),
    labelSmall = imperialStyle(display, 11, 16),
  )
}

/**
 * One level of the scale above, keeping the metrics the `:app` original used.
 *
 * `letterSpacing` is pinned to zero rather than left to the Material default, because the calligraphic
 * faces carry their own rhythm and the default tracking visibly loosened the strokes.
 */
private fun imperialStyle(fontFamily: FontFamily, fontSize: Int, lineHeight: Int): TextStyle =
  TextStyle(
    fontFamily = fontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = 0.sp,
  )
