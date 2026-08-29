# UI Module Design

> **Superseded notice:** This document described the Android-first cycle. The project has migrated to a Kotlin Multiplatform shared kernel with an iOS-first app (`:iosApp`, iPhone + iPad); the legacy `:app` Android app is frozen except bug fixes. See [07-ios-migration-plan.md](./07-ios-migration-plan.md). The iOS shell is native SwiftUI; Compose Multiplatform content panes are bridged via `UIViewControllerRepresentable`. Content below that is still valid is preserved; Android/foldable-specific references are annotated.

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. UI references include title `聚合拾遗` near line 246, parser near line 251, detail tabs near line 293, settings controls near line 318, and card or topic modals near line 389.

Repo references:

- Legacy Android: `app/build.gradle.kts` includes Compose UI, Material3, preview tooling, and Compose UI test libraries; `MainActivity.kt` hosted the Android app shell inside `ArchiveAssistantTheme`; instrumented UI tests lived under `app/src/androidTest/java/com/lyihub/archiveassistant/`. The `:app` module is frozen except bug fixes.
- iOS-first: `:iosApp` Xcode project + Swift sources (builds only on macOS). The shell is native SwiftUI (`WindowGroup`, `NavigationSplitView` for iPad, multi-window). Settings are native SwiftUI (Form, Picker, SecureField, SF Symbols). Home/detail/memorial content panes are Compose Multiplatform.
- Compose is bridged to SwiftUI via `UIViewControllerRepresentable` in `ComposeHostingViewController.swift`. Known constraint: `ComposeUIViewController` gives no reliable intrinsic content size, so explicit sizing is required or the view collapses to zero height.

## Compose Components

On iOS the Compose components render inside the `ComposeHostingViewController` bridge; Settings itself is native SwiftUI (Form, Picker, SecureField, SF Symbols). The component list below describes the Compose content panes (home, detail, memorial) plus the legacy Android UI:

- `ArchiveAssistantApp`: top-level state holder for pane, selected topic, selected tab, settings, and modal state.
- `HomePane`: title, subtitle, parser card, classify button, recent topic list, create action, manage action.
- `ParserCard`: multiline input, paste or file actions, support hint, `智能归纳` button.
- `RecentTopicList`: topic rows or cards with item count and updated state.
- `DetailPane`: selected topic header, `ContentTypeTabs`, `KnowledgeFeed`, and empty state.
- `KnowledgeCard`: compact item preview with the enum-derived content type label and title.
- `KnowledgeCardModal`: detailed content panel with close action.
- `SettingsPane`: engine type selector, cloud fields, local fields, masked key field.
- `TopicManagePane`: all-topic list, create, rename, and select actions.
- `TopicNameDialog`: create or rename dialog with 20-character counter.

## Foldable Breakpoints And Posture Rules

- Compact width: one pane. Home, Detail, Settings, and Manage are separate destinations.
- Medium width: prefer one pane unless content width and posture are proven safe for two panes.
- Expanded width: use two panes, Home as master and Detail, Settings, or Manage as the right pane.
- Half-open posture: avoid hinge overlap and keep primary controls on one physical panel.
- Tabletop posture: keep typing and action controls away from the fold. Read-only detail can occupy the secondary area if comfortable.
- Unknown posture: use width class only and keep modal max width bounded.

## Hinge Avoidance

- Use hinge bounds to create safe content areas when available.
- Keep parser input, tabs, settings fields, dialog actions, and card modal buttons fully inside one safe area.
- Do not center a modal across a hinge on foldables.
- In expanded two-pane mode, place the pane divider outside hinge bounds or align pane split with the hinge when that produces two usable panels.

## iOS Sizing Constraint

Because `ComposeUIViewController` exposes no reliable intrinsic content size, every Compose content pane hosted on iOS must get explicit sizing (via SwiftUI frame or autolayout constraints); otherwise the view collapses to zero height. This applies to home, detail, and memorial panes.

## vivo foldable guideline decisions

> These decisions apply to the legacy Android `:app` UI (frozen). On the iOS-first app, responsive layout is handled by SwiftUI `NavigationSplitView` on iPad rather than Android width classes/hinge bounds. The section is preserved for the Android legacy surface.

Official reference attempted: `https://dev.vivo.com.cn/documentCenter/doc/597`. The fetch returned only the vivo open platform shell title, so exact page text was not available in this environment. Apply the required vivo foldable guideline wording as implementation decisions:

- `可调整窗口` (resizeable window): `AndroidManifest.xml` sets `android:resizeableActivity="true"` on `MainActivity`.
- `无黑边` (no black bars): Activity uses edge-to-edge rendering with `enableEdgeToEdge()` and theme backgrounds match surface colors, avoiding letterboxing.
- `非整体放大` (no whole-screen zoom): Layout uses width-based breakpoints (`LayoutMode.COMPACT` < 600 dp, `LayoutMode.EXPANDED` >= 840 dp) with recomposition rather than system-level scaling.
- `展开态展示更多内容` (unfolded state shows more content): `ArchiveAssistantApp` switches from single-pane to two-pane `Row` when `WindowLayoutInfo.shouldShowTwoPanes` is true, keeping `HomePane` as master and placing Detail/Settings/Manage in the secondary pane.

Implementation mapping:
- `ui/layout/LayoutMode.kt` defines `LayoutMode` enum (`COMPACT`, `EXPANDED`, `FOLDABLE`), `WindowLayoutInfo`, and `HingeBounds` for testable hinge-safe areas.
- `ui/layout/LayoutMode.kt` provides `shouldShowTwoPanes(selectedTopicId)` so the shell decides master/detail based on width and selection, not posture alone.
- `ArchiveAssistantApp` reads `rememberWindowLayoutInfo()` and renders `SinglePaneLayout` or `TwoPaneLayout`.
- In `TwoPaneLayout`, when `hingeBounds` are present, a `hinge-spacer` Box occupies hinge width to prevent content from crossing the fold; otherwise a `VerticalDivider` separates panes.
- Avoid placing important content or controls in hinge and crease areas.
- Keep continuity when switching posture, preserving selected topic, parser text, active tab, and settings edits.
- Use two-pane master/detail only when the unfolded width and hinge-safe areas support it.
- Test on vivo foldable hardware or a matching emulator profile before considering foldable support complete.

## Test Tags

Stable Compose test tags use dash-style names from the current implementation:

- `parser-input`: parser multiline text input in `HomePane`.
- `classify-button`: local-only classify action in `HomePane`.
- `recent-topic-list`: recent topics container in `HomePane`.
- `topic-card-{id}`: selectable recent topic card in `HomePane`, where `{id}` is the topic id.
- `manage-button`: manage-all-topics action in `HomePane`.
- `create-topic-button`: create topic action in `ManagePane`.
- `rename-topic-button-{id}`: per-topic rename action in `ManagePane`, where `{id}` is the topic id.
- `delete-topic-button-{id}`: per-topic delete action in `ManagePane`, where `{id}` is the topic id.
- `detail-tabs`: content-type tab row in `DetailPane`.
- `knowledge-card-{id}`: selectable knowledge item card in `DetailPane`, where `{id}` is the item id.
- `card-modal`: modal container for an opened knowledge card.
- `settings-trigger`: settings action in `HomePane`.
- `engine-type-selector`: AI engine type selector in `SettingsPane`.

Supporting tags also exist for pane roots, settings fields, dialog controls, and hinge spacing, but the tags above are the primary action and navigation selectors used for regression tests.

## Guardrails

- Must NOT use WebView for the prototype.
- Must NOT place buttons, input fields, tabs, or modal actions across the hinge (legacy Android foldable surface).
- Must NOT present a Compose view on iOS without explicit sizing.
- The `智能归纳` button may make real AI calls: remote OpenAI-compatible `/chat/completions`, OpenAI Responses `/responses`, Anthropic `/messages`, Gemini `:generateContent`, or on-device LiteRT-LM inference via `LocalLlmEngine` (model GEMMA_4_E4B_IT; NPU/GPU/CPU backends with automatic fallback). This supersedes the earlier "Must NOT make real AI API calls from `智能归纳`" guardrail.

## Acceptance Checks

- Compose tests locate all required test tags.
- Compact preview and expanded preview show usable navigation without clipped text.
- vivo foldable QA confirms hinge avoidance for Home, Detail, Settings, Manage, and modals.
