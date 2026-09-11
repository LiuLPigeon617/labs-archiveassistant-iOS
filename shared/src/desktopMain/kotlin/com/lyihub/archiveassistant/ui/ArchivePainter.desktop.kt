package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Desktop target exists only to compile-check the shared UI on a developer machine, so bundled art
 * is not resolved here; callers use their placeholder instead.
 */
@Composable
actual fun archivePainter(assetName: String): Painter? = null
