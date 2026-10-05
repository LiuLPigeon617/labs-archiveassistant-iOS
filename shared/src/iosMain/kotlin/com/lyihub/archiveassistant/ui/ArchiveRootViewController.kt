package com.lyihub.archiveassistant.ui

import androidx.compose.ui.window.ComposeUIViewController
import com.lyihub.archiveassistant.domain.AppPane
import com.lyihub.archiveassistant.state.ArchiveAssistantState
import com.lyihub.archiveassistant.state.ArchiveAssistantStateStore
import platform.UIKit.UIViewController

/**
 * Swift-facing factory for the shared Compose root.
 *
 * This is the piece the iOS shell was waiting for. `ComposeHostingViewController` in `iosApp` used
 * to render a SwiftUI stand-in because `SharedKit` exposed no `ComposeUIViewController` factory at
 * all; it can now hand the real Compose tree to UIKit.
 *
 * Kotlin/Native traps worth remembering when calling this from Swift:
 *
 *  - A top-level Kotlin function is exported into a file-named class, so calling it from Swift needs
 *    the file facade name (`ArchiveRootViewControllerKt.createArchiveRootViewController(...)`).
 *    Everything Swift calls on purpose therefore lives on the [IosComposeRoot] object instead.
 *  - `ComposeUIViewController` does not report a reliable intrinsic content size, so the hosted view
 *    must be pinned with explicit constraints by the caller (see `ComposeHostingViewController.swift`).
 *
 * No system-appearance plumbing is done here on purpose: the imperial palette is resolved inside the
 * Compose tree by `ArchiveAssistantTheme` (which does read `isSystemInDarkTheme()`), so the host has
 * nothing to configure — toggling the simulator appearance and relaunching is enough.
 */
object IosComposeRoot {
  /**
   * The store the preview root renders.
   *
   * Starts on [AppPane.TOPICS], which is also the store's own default — it is spelled out here so the
   * entry point does not silently follow a future change to that default. `TOPICS` renders the
   * dashboard, the app's main screen; the preview used to open on settings only because settings was
   * the sole migrated pane.
   */
  fun makeViewController(): UIViewController =
    createArchiveRootViewController(
      ArchiveAssistantStateStore(
        initialState = ArchiveAssistantState(selectedPane = AppPane.TOPICS)
      )
    )
}

private fun createArchiveRootViewController(
  stateStore: ArchiveAssistantStateStore
): UIViewController = ComposeUIViewController { ArchiveAssistantRoot(stateStore = stateStore) }
