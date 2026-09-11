package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * iOS asset loading.
 *
 * Compose Multiplatform on iOS resolves resources through the generated `Res` class, which requires
 * the assets to be present in `commonMain/composeResources`. The mock art has not been moved there
 * yet, so this returns null and callers fall back to a placeholder.
 */
@Composable
actual fun archivePainter(assetName: String): Painter? = null
