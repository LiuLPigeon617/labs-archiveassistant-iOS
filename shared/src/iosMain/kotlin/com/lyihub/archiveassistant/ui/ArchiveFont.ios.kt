package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * iOS font loading. The three calligraphic TTFs are not yet in commonMain/composeResources, so this
 * returns null and the theme falls back to the platform serif family.
 */
@Composable
actual fun archiveFontFamily(assetName: String): FontFamily? = null
