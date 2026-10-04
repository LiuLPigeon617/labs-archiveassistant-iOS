package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import juheshiyi.shared.generated.resources.Res
import juheshiyi.shared.generated.resources.home_ornament_clipboard_sanxingdui
import juheshiyi.shared.generated.resources.imperial_ornament_pattern
import juheshiyi.shared.generated.resources.memorial_button_bg
import juheshiyi.shared.generated.resources.memorial_completion_bg
import juheshiyi.shared.generated.resources.memorial_cover_corner
import juheshiyi.shared.generated.resources.memorial_cover_pattern
import juheshiyi.shared.generated.resources.memorial_stamp_dislike
import juheshiyi.shared.generated.resources.memorial_stamp_like
import juheshiyi.shared.generated.resources.memorial_touch_book
import juheshiyi.shared.generated.resources.pending_note_stamp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/**
 * Loads a bundled image by asset name.
 *
 * The Android build referenced `R.drawable.*` integer ids, which do not exist outside `:app`. The
 * shared kernel therefore carries asset *names* and resolves them here through the Compose
 * Multiplatform resource pack in `commonMain/composeResources`, which compiles the same art into
 * every target.
 *
 * Returns `null` when the asset is not (yet) bundled, so callers degrade to a placeholder instead of
 * crashing. [bundledDrawable] is the single source of truth for which names resolve — keep it in
 * sync with the contents of `composeResources/drawable`.
 */
@Composable
fun archivePainter(assetName: String): Painter? =
  bundledDrawable(assetName)?.let { painterResource(it) }

/**
 * The names currently bundled in `commonMain/composeResources/drawable`.
 *
 * Assets still living only in `:app` (`app/src/main/res`) are deliberately absent: they resolve to
 * `null` and their call sites fall back. Adding one is a two-step change — copy the file into
 * `composeResources/drawable`, then add its branch here.
 */
private fun bundledDrawable(assetName: String): DrawableResource? =
  when (assetName) {
    "home_ornament_clipboard_sanxingdui" -> Res.drawable.home_ornament_clipboard_sanxingdui
    "imperial_ornament_pattern" -> Res.drawable.imperial_ornament_pattern
    "memorial_button_bg" -> Res.drawable.memorial_button_bg
    "memorial_completion_bg" -> Res.drawable.memorial_completion_bg
    "memorial_cover_corner" -> Res.drawable.memorial_cover_corner
    "memorial_cover_pattern" -> Res.drawable.memorial_cover_pattern
    "memorial_stamp_dislike" -> Res.drawable.memorial_stamp_dislike
    "memorial_stamp_like" -> Res.drawable.memorial_stamp_like
    "memorial_touch_book" -> Res.drawable.memorial_touch_book
    "pending_note_stamp" -> Res.drawable.pending_note_stamp
    else -> null
  }

/**
 * [archivePainter] for call sites that need a non-null `Painter` — `Image(painter = …)` and any
 * helper whose parameter was already non-null before the migration.
 *
 * Assets that are not bundled yet yield a transparent painter of a plausible size rather than a
 * crash, so layout is preserved and the surrounding paper tint shows through.
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
