package com.lyihub.archiveassistant.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import juheshiyi.shared.generated.resources.Res
import juheshiyi.shared.generated.resources.dinglie_song_typeface
import juheshiyi.shared.generated.resources.san_ji_xing_kai_jian_ti_cu
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource

/**
 * Loads a bundled calligraphic font by asset name (without extension), e.g.
 * `dinglie_song_typeface`.
 *
 * The Android build referenced `R.font.*` ids, which do not exist outside `:app`. The shared kernel
 * therefore carries font *names* and resolves them here through the Compose Multiplatform resource
 * pack in `commonMain/composeResources/font`, which compiles the same TTFs into every target.
 *
 * Returns null when the font is not bundled, letting the theme fall back to the platform serif.
 * [bundledFont] is the single source of truth for which names resolve — keep it in sync with the
 * contents of `composeResources/font`.
 *
 * Callers: `ArchiveAssistantRoot`, which installs the resolved faces through `ProvideImperialFonts`.
 * Before that root existed nothing called this, so `LocalImperialFonts` always held its
 * `FontFamily.Serif` default and every pane rendered fallback glyphs.
 */
@Composable
fun archiveFontFamily(assetName: String): FontFamily? =
  bundledFont(assetName)?.let { FontFamily(Font(it, FontWeight.Normal)) }

/**
 * The font names currently bundled in `commonMain/composeResources/font`.
 *
 * `ma_shan_zheng_regular` is deliberately absent: it sits in `:app`'s `res/font` but no code
 * references it, so bundling its 5.6 MB would cost size for nothing. Adding one is a two-step
 * change — copy the TTF into `composeResources/font`, then add its branch here.
 */
private fun bundledFont(assetName: String): FontResource? =
  when (assetName) {
    "dinglie_song_typeface" -> Res.font.dinglie_song_typeface
    "san_ji_xing_kai_jian_ti_cu" -> Res.font.san_ji_xing_kai_jian_ti_cu
    else -> null
  }
