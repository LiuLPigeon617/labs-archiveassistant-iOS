# Verification And QA Plan

> **Superseded notice:** This document described the Android-first cycle. The project has migrated to a Kotlin Multiplatform shared kernel with an iOS-first app (`:iosApp`); the legacy `:app` Android app is frozen except bug fixes. See [07-ios-migration-plan.md](./07-ios-migration-plan.md). Shared-kernel verification runs on any OS; iOS builds and the iOS app require macOS. The "no real AI API calls during QA" guardrail is corrected below. Content that is still valid is preserved.

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. QA should cover parser flow near line 251, detail tabs near line 293, settings near line 308, manage pane near line 376, and modals near line 389.

Repo references:

- Root Gradle project: `settings.gradle.kts`, project name `ArchiveAssistant`, modules `:shared` (KMP kernel), `:app` (legacy Android, frozen except bug fixes), `:iosApp` (Xcode project, builds only on macOS).
- Shared kernel sources: `shared/src/commonMain` (domain/, data/, platform/, state/) with `androidMain`, `iosMain`, `jvmMain`. Targets: jvm, android, iosArm64, iosSimulatorArm64.
- Legacy Android app config: `app/build.gradle.kts`, package `com.lyihub.archiveassistant`, Compose and Material3 enabled.
- Legacy Android entry point: `app/src/main/java/com/lyihub/archiveassistant/MainActivity.kt`; local tests `app/src/test/...`; instrumented tests `app/src/androidTest/...`.
- iOS: `:iosApp` Xcode project + Swift sources; native SwiftUI shell with Compose Multiplatform content panes bridged via `ComposeHostingViewController.swift`.

## Gradle Commands

Run from repo root. Shared-kernel verification works on any OS:

```bash
./gradlew :shared:compileKotlinJvm :shared:jvmTest
```

Expected outcomes for the current repo state:

- `./gradlew :shared:compileKotlinJvm :shared:jvmTest`: compiles the shared kernel and passes all JVM tests (15/15 green currently, across 30 test files) for domain classification, state, layout mode, friendly time, and persistence.

macOS-only commands (iOS compilation is impossible on Windows; Kotlin/Native needs the Apple SDK and signing needs Xcode):

```bash
./gradlew :shared:compileKotlinIosSimulatorArm64
./gradlew :shared:assembleSharedKitXCFramework
```

Legacy Android `:app` commands (frozen surface):

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:compileDebugAndroidTestKotlin
./gradlew :app:connectedDebugAndroidTest
```

- `:app:connectedDebugAndroidTest` must be attempted when a device or emulator is attached. If the connected device rejects install approval, record the exact blocker, currently `INSTALL_FAILED_ABORTED: User rejected permissions`, and treat it as a device-side environment blocker rather than a code failure.

CI plan: a GitHub Actions macOS runner produces an UNSIGNED IPA for verification; unsigned artifacts cannot be installed on physical devices.

For foldable QA on the legacy Android surface, prefer a real vivo foldable or a device profile that exposes width changes and posture or hinge data.

## Documentation Checks

```bash
test -d AI-Design
ls AI-Design
rg "Must NOT" AI-Design
# Guard against regressions to the "no real AI" guardrails. The -F flag is required so the
# pattern is matched literally; without it the pipe alternation is treated as a regex.
rg -F -e "no real AI API calls" -e "No Network Validation" -e "Must NOT make real AI API calls" AI-Design
```

Expected file list:

- `00-overall-implementation-plan.md`
- `01-product-functional-analysis.md`
- `02-information-architecture.md`
- `03-data-and-state-model.md`
- `04-ui-module-design.md`
- `05-ai-engine-settings-design.md`
- `06-verification-and-qa-plan.md`
- `07-ios-migration-plan.md`

The guardrail grep should return no false "no real AI API calls" strings, since remote and on-device AI are implemented.

## Functional QA Cases

- Parser: enter text, trigger `智能归纳`, see a classified item appear under a topic. This may invoke remote AI (OpenAI-compatible `/chat/completions`, OpenAI Responses `/responses`, Anthropic `/messages`, Gemini `:generateContent`) or on-device LiteRT-LM inference via `LocalLlmEngine`; responses are forced to strict JSON and parsed via shared `extractJsonObject()`.
- Recent topics: create a topic, select it, and confirm Detail opens with that topic title. (Note: the six-ministries taxonomy is immutable at runtime; topic create/rename/delete are rejected with `六部分类已固定，不能新建、重命名或删除。`, and unknown `topicId` resolves to 户·府库.)
- Detail tabs: switch between `全部`, `网页文章`, `图像截屏`, and `文档/PDF`, confirming feed filtering.
- Card modal: open a card and close the modal without losing selected topic or tab.
- Topic manage: open `全部主题`, create or rename a topic, and return to Detail or Home.
- Settings: open settings, switch `API` and `本地模型`, enter an API key, confirm masking. (On iOS this is native SwiftUI with `SecureField`.)
- Endpoint probes: `AiEndpointLatencyTester` performs real endpoint latency checks against configured providers.

## vivo Foldable Layout QA Cases

> These cases apply to the legacy Android `:app` surface (frozen). On the iOS-first app, responsive layout uses SwiftUI `NavigationSplitView` on iPad.

- Folded compact: Home, Detail, Settings, and Manage are single-pane destinations with working back navigation.
- Unfolded expanded: Home remains visible while Detail, Settings, or Manage occupies the right pane.
- Half-open or tabletop, when exposed: parser input, tabs, settings fields, and modal actions avoid hinge bounds.
- Rotation and posture change: selected topic, parser text, active tab, modal dismissal state, and settings edits remain coherent.
- Official vivo reference: `https://dev.vivo.com.cn/documentCenter/doc/597`. If the page shell blocks content, record that limitation and verify against the applied foldable decisions in `04-ui-module-design.md`.

## Evidence Paths

Store implementation evidence under `.sisyphus/evidence/knowledge-curation-app/`:

- `.sisyphus/evidence/knowledge-curation-app/task-01-docs-existence.md`
- `.sisyphus/evidence/knowledge-curation-app/task-02-domain-classifier-tests.md`
- `.sisyphus/evidence/knowledge-curation-app/task-03-state-entry-tests.md`
- `.sisyphus/evidence/knowledge-curation-app/task-04-responsive-shell.md`
- `.sisyphus/evidence/knowledge-curation-app/task-05-parser-classification.md`
- `.sisyphus/evidence/knowledge-curation-app/task-06-topic-management.md`
- `.sisyphus/evidence/knowledge-curation-app/task-07-detail-feed-modal.md`
- `.sisyphus/evidence/knowledge-curation-app/task-08-settings-pane.md`
- `.sisyphus/evidence/knowledge-curation-app/task-09-datastore-persistence.md`
- `.sisyphus/evidence/knowledge-curation-app/task-10-final-consolidation.md`
- `.sisyphus/evidence/knowledge-curation-app/verification-results.md`

## Guardrails

- Must NOT count docs as implementation proof for runtime behavior.
- Must NOT skip real-device testing before expanding beyond the first usable module.
- iOS QA must NOT claim success without a macOS build; iOS compilation is impossible on Windows and unsigned artifacts cannot be installed on physical devices.
- Remote and on-device AI are implemented, so QA may exercise real endpoints (including `AiEndpointLatencyTester` probes). Use configured test endpoints and avoid real secrets in QA artifacts.

## Acceptance Checks

- Docs verification commands pass.
- Later Gradle checks pass or capture pre-existing failures in `.sisyphus/evidence/knowledge-curation-app/`.
- Foldable QA records device, posture, layout mode, and hinge findings.
