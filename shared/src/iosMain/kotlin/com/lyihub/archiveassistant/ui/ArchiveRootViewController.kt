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
 * No system-appearance plumbing is done here on purpose: the imperial palette has no dark variant
 * yet (see the theme gap noted on `ArchiveAssistantRoot`), so reacting to light/dark before that
 * theme exists would only re-render the same colours.
 */
object IosComposeRoot {
  /**
   * The store the preview root renders.
   *
   * Opened on [AppPane.SETTINGS] rather than the store's default [AppPane.TOPICS], because settings
   * is the only pane migrated into `:shared`. Starting on `TOPICS` would land the preview on the
   * "not yet migrated" message, which verifies nothing — and this entry point exists to verify that
   * the Compose resource pipeline and the imperial fonts actually render on iOS.
   */
  fun makeViewController(): UIViewController =
    createArchiveRootViewController(
      ArchiveAssistantStateStore(
        initialState = ArchiveAssistantState(selectedPane = AppPane.SETTINGS)
      )
    )
}

private fun createArchiveRootViewController(
  stateStore: ArchiveAssistantStateStore
): UIViewController = ComposeUIViewController { ArchiveAssistantRoot(stateStore = stateStore) }
