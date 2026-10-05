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
import com.lyihub.archiveassistant.ui.screens.HomePane
import com.lyihub.archiveassistant.ui.screens.SettingsPane
import com.lyihub.archiveassistant.ui.screens.TopicManagementDialogs
import com.lyihub.archiveassistant.ui.theme.ArchiveAssistantTheme
import com.lyihub.archiveassistant.ui.theme.ImperialFonts
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
 * ### What it dispatches to, and what is missing
 *
 * [HomePane] (the dashboard) and [SettingsPane] are migrated, so those two panes render. Everything
 * else — `DetailPane`, `MemorialBriefingPane` — still lives in the Android `:app` module, so the
 * `else` branch says so instead of rendering an empty box.
 *
 * The dispatch is keyed on [AppPane] because the store already carries that field on every target;
 * no extra navigation state had to be invented for this root.
 *
 * ### Known gap: no Material theme
 *
 * When this root was first written, the module had no `MaterialTheme` wrapper, so `SettingsPane` read
 * `MaterialTheme.colorScheme.primary` and friends against the Material 3 **baseline** scheme. That is
 * no longer the case: this root now installs [ArchiveAssistantTheme], which supplies both the imperial
 * colour scheme and the calligraphic type scale. `ProvideImperialFonts` is still installed as well,
 * because call sites that read `LocalImperialFonts` directly (e.g. `PaneHeroHeader`) would otherwise
 * fall back to serif.
 *
 * @param stateStore the store backing every screen. Defaults to a fresh one.
 */
@Composable
fun ArchiveAssistantRoot(stateStore: ArchiveAssistantStateStore = remember { defaultStateStore() }) {
  ProvideImperialFonts(fonts = bundleImperialFonts()) {
    ArchiveAssistantTheme {
      val state = stateStore.state

      if (state.selectedPane == AppPane.TOPICS) {
        HomePane(
          title = "聚合拾遗",
          // Mirrors the Android host: while a search is active the dashboard shows only the topics
          // that matched, which is why this is `searchedTopics` rather than `recentTopics`.
          recentTopics =
            if (state.homeSearchQuery.isBlank()) state.topics else state.searchedTopics,
          itemsByTopic = state.itemsByTopic,
          searchQuery = state.homeSearchQuery,
          parserValidationMessage = state.parserValidationMessage,
          smartSummarizationMessage = state.smartSummarizationMessage,
          onTopicSelected = stateStore::openTopic,
          onOpenSettings = stateStore::openSettings,
          onCreateTopic = stateStore::openAddItemDialog,
          onRenameTopic = stateStore::openRenameTopicDialog,
          onDeleteTopic = stateStore::openDeleteConfirmDialog,
          onSearchQueryChanged = stateStore::updateHomeSearchQuery,
          onOpenClipboard = stateStore::openLatestClipboardDialog,
          onOpenMemorialDemo = stateStore::openMemorialBriefing,
        )
      } else if (state.selectedPane == AppPane.SETTINGS) {
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

      // Dialogs sit outside the pane dispatch, exactly as they do in the Android host: HomePane only
      // *requests* a rename or a delete through its callbacks, and without this the "管理" flow would
      // be visibly broken — the buttons would do nothing at all.
      TopicManagementDialogs(
        topics = state.topics,
        topicNameDialogMode = state.topicNameDialogMode,
        topicNameDialogTopicId = state.topicNameDialogTopicId,
        topicValidationMessage = state.topicValidationMessage,
        deleteConfirmTopicId = state.deleteConfirmTopicId,
        onConfirmCreateTopic = stateStore::confirmCreateTopic,
        onConfirmRenameTopic = stateStore::confirmRenameTopic,
        onConfirmDeleteTopic = stateStore::confirmDeleteTopic,
        onCloseTopicNameDialog = stateStore::closeTopicNameDialog,
        onCloseDeleteConfirmDialog = stateStore::closeDeleteConfirmDialog,
      )
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
  // Reads the scheme rather than the ImperialIvory literal it used before the theme existed, so the
  // message stays legible in dark mode too. Text colour was already scheme-driven.
  Box(
    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "共享 Compose 层还没有可显示的面板。\n主页与设置页已就绪，详情页与奏折页仍在迁移中。",
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(24.dp),
    )
  }
}

private fun defaultStateStore(): ArchiveAssistantStateStore = ArchiveAssistantStateStore()
