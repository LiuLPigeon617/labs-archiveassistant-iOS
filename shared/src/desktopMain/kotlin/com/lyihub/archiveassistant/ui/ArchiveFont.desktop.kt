package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/** Desktop target is for compile checks only; bundled fonts are not resolved. */
@Composable
actual fun archiveFontFamily(assetName: String): FontFamily? = null
