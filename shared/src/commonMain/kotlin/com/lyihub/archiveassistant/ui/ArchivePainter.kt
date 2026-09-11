package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

/**
 * Loads a bundled image by asset name.
 *
 * The Android build referenced `R.drawable.*` integer ids, which do not exist outside `:app`. The
 * shared UI therefore carries asset *names* and each platform resolves them its own way: Android via
 * `res/drawable`, iOS via `Bundle.main`.
 *
 * Returns `null` when the asset is absent, so callers can fall back to a placeholder instead of
 * crashing on a missing resource.
 */
@Composable
expect fun archivePainter(assetName: String): Painter?
