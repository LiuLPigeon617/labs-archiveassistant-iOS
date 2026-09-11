package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * Loads a bundled font by asset name (without extension), e.g. `dinglie_song_typeface`.
 *
 * The Android build referenced `R.font.*` ids, which do not exist outside `:app`, so the shared theme
 * carries font *names* and each platform resolves them. Returns null when unavailable, letting the
 * theme fall back to the platform default serif/sans family.
 */
@Composable
expect fun archiveFontFamily(assetName: String): FontFamily?
