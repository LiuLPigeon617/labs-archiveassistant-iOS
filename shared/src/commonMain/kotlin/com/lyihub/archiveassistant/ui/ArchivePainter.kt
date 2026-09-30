package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
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

/**
 * [archivePainter] for call sites that need a non-null `Painter` — `Image(painter = …)` and any
 * helper whose parameter was already non-null before the migration.
 *
 * Until bundled art moves into `commonMain/composeResources`, non-Android targets resolve nothing,
 * so this returns a transparent painter of a plausible size instead of crashing. Draw order is
 * preserved and the surrounding paper tint shows through, which matches how the panes already
 * degrade for absent `res/drawable` entries.
 */
@Composable
fun archivePainterOrPlaceholder(
  assetName: String,
  width: Int = 1,
  height: Int = 1,
): Painter = archivePainter(assetName) ?: rememberTransparentPainter(width, height)

@Composable
private fun rememberTransparentPainter(width: Int, height: Int): Painter =
  remember(width, height) {
    BitmapPainter(ImageBitmap(width = width.coerceAtLeast(1), height = height.coerceAtLeast(1)))
  }
