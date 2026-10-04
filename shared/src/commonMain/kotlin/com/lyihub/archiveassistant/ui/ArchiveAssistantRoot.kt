package com.lyihub.archiveassistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.lyihub.archiveassistant.domain.AppPane
import com.lyihub.archiveassistant.state.ArchiveAssistantStateStore
import com.lyihub.archiveassistant.ui.screens.SettingsPane
import com.lyihub.archiveassistant.ui.theme.ImperialFonts
import com.lyihub.archiveassistant.ui.theme.ImperialIvory
import com.lyihub.archiveassistant.ui.theme.ProvideImperialFonts

/**
 * The shared Compose composition root — the first entry point that exists on every target.
 *
 * Previously the only composition root was `ArchiveAssistantApp` in the Android `:app` module, so
 * nothing in this module was ever *rendered*: settings, fonts and Resources were compiled and
 * tested but had no top. This is that top, and it is deliberately small, because the panes it
 * would dispatch to have not been migrated yet:
 *
 *  - **Imperial fonts are installed here.** `ProvideImperialFonts` had no caller anywhere, so
 *    `LocalImperialFonts` always held its `FontFamily.Serif` default and every pane — including on
 *    Android — has been rendering fallback serif glyphs. Bundling the two calligraphic faces in
 *    `composeResources/font/` changed nothing until this function existed.
 *  - **`ArchiveAssistantStateStore` is constructed by default.** Its constructor is already
 *    platform-free (every parameter has a default, and the defaults seed the built-in sample
 *    data), so the root needs no dependency-injection scaffolding to run.
 *
 * ### Why this renders settings, and what is missing
 *
 * `SettingsPane` is the only fully migrated screen, so it is what the root can show. `HomePane` and
 * `DetailPane` still live in the Android `:app` module, so there is no pane switching here yet —
 * whatever replaces this dispatch must arrive with those screens.
 *
 * ### Known gap: no Material theme
 *
 * This module has no `MaterialTheme` wrapper, and this root does not add one. `SettingsPane` reads
 * `MaterialTheme.colorScheme.primary`, `.error` and `.onSurfaceVariant`, and `PaneDivider` reads
 * `.outlineVariant`, so those still resolve to the Material 3 **baseline** scheme rather than to the
 * imperial palette in [com.lyihub.archiveassistant.ui.theme.ImperialPalette]. Adding that theme is
 * deliberately its own change; see `AI-Design/07-ios-migration-plan.md`.
 *
 * @param stateStore the store backing every screen. Defaults to a fresh one.
 */
@Composable
fun ArchiveAssistantRoot(stateStore: ArchiveAssistantStateStore = remember { defaultStateStore() }) {
  val fonts = bundleImperialFonts()
  ProvideImperialFonts(fonts = fonts) {
    val state = stateStore.state

    if (state.selectedPane == AppPane.SETTINGS) {
      SettingsPane(
        aiSettings = state.aiSettings,
        onAiSettingsChanged = stateStore::updateAiSettings,
        onBack = stateStore::closePanes,
        localModelState = state.localModelState,
        benchmarkResult = state.benchmarkResult,
        isBenchmarkRunning = state.isBenchmarkRunning,
        onDownloadModel = stateStore::downloadModel,
        onCancelDownload = stateStore::cancelDownload,
        onStartModel = stateStore::startModel,
        onStopModel = stateStore::stopModel,
        onBackendPreferenceChange = stateStore::updateBackendPreference,
        onRunBenchmark = stateStore::runBenchmark,
        // Left inert on purpose: choosing a model file needs a native file picker that does not
        // exist yet, and a callback that pretends to work would be worse than one that is visibly
        // absent. The button is still drawn; tapping it does nothing.
        onChooseModelFile = {},
      )
    } else {
      // No other pane is migrated into this module yet. Say so rather than rendering an empty box,
      // which would be indistinguishable from a layout bug.
      NotYetMigratedPane()
    }
  }
}

/**
 * Resolves the two bundled calligraphic faces.
 *
 * Allocates nothing from the platform: the fonts are read out of `composeResources/font/` by the
 * generated accessors, so this is the same code on iOS, Android and desktop. The `?: FontFamily.Serif`
 * fallbacks are what the theme held for every pane until this root existed, so a missing font
 * degrades to the old behaviour rather than to no text at all.
 *
 * `archiveFontFamily` is itself `@Composable` (the Compose resource API loads the face through
 * composition), so these calls must stay inline in the composable body — wrapping them in `remember`
 * puts them in a non-composable lambda and fails to compile. The resource layer caches the loaded
 * bytes, so calling this on each recomposition does not re-read the TTF.
 */
@Composable
private fun bundleImperialFonts(): ImperialFonts =
  ImperialFonts(
    title = archiveFontFamily("san_ji_xing_kai_jian_ti_cu") ?: FontFamily.Serif,
    display = archiveFontFamily("dinglie_song_typeface") ?: FontFamily.Serif,
  )

@Composable
private fun NotYetMigratedPane() {
  Box(modifier = Modifier.fillMaxSize().background(ImperialIvory), contentAlignment = Alignment.Center) {
    Text(
      text = "共享 Compose 层还没有可显示的面板。\n设置页已就绪，主页与详情页仍在迁移中。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(24.dp),
    )
  }
}

private fun defaultStateStore(): ArchiveAssistantStateStore = ArchiveAssistantStateStore()
