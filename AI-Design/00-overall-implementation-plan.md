# Overall Implementation Plan

> **Status: superseded by the iOS migration.** This document described the original Android-only
> build. The project is now migrating to a Kotlin Multiplatform shared kernel with an iOS-first app
> (iPhone + iPad). See [07-ios-migration-plan.md](07-ios-migration-plan.md) for the current plan.
> The Android `:app` module remains in the tree and still builds, but is no longer the target.

## Current Target

- **Primary**: iOS app (iPhone + iPad), native SwiftUI shell + Compose Multiplatform content panes.
- **Shared**: Kotlin Multiplatform module (`:shared`) holding domain, data, and state.
- **Android**: retained for the transition; not receiving new features.

## Module Layout

| Module | Role |
|---|---|
| `:shared` | KMP kernel: `domain`, `data`, `state`, `platform`. Targets: `jvm` (verification), `android`, `iosArm64`, `iosSimulatorArm64`. |
| `:app` | Legacy Android Compose app. Frozen except for bug fixes. |
| `:iosApp` | Xcode project + Swift sources. Builds only on macOS. |

## Historical Android Scope

Below is retained for context on what the Android build covered.

Original prototype: `knowledge-curation-app-11.html` — body shell near line 230, parser area near
line 251, detail pane near line 285, settings pane near line 308, manage pane near line 376.

Android feature modules that were built:

- Home parser: accepted text, pasted content, links, file references, and image/document descriptions
  as local input objects. Prototype label: `拖拽文件、输入链接、纯文本，或直接从剪切板粘贴...`.
- AI classify flow: `智能归纳` action assigning content to topics.
- Recent topics: `最近主题` list with create and all-topic entry points.
- Detail reader: selected topic title, tabs `全部` / `网页文章` / `图像截屏` / `文档 PDF`, card feed,
  detail modal.
- Topic manage: `全部主题`, create, rename, basic topic actions.
- Settings: `配置应用设置`, cloud or local engine mode, Base URL, API key, cloud model, local model.
- Foldable adaptation: compact single-pane, expanded master/detail, tabletop and half-open behavior.

## Execution Order (historical, completed)

1. Domain model and seed data for `Topic`, `KnowledgeItem`, `ContentType`, `AiEngineSettings`, layout state.
2. Home parser and topic list replacing the starter `Greeting` screen.
3. Detail pane and tabbed item filtering.
4. Card modal and topic create/rename modal.
5. Settings pane with local-only persistence and masked key display.
6. Manage pane and expanded layout behavior.
7. Foldable posture handling, hinge avoidance, device QA.

## Superseded Guardrails

The following guardrails applied to the first Android implementation cycle and **no longer hold**:

| Old guardrail | Current reality |
|---|---|
| Must NOT make real AI API calls | Remote AI is implemented: OpenAI-compatible, OpenAI Responses, Anthropic, Gemini. |
| Must NOT validate API keys against a remote service | A latency tester performs real endpoint probes. |
| Classify by local fake data or deterministic rules only | `RemoteApiSmartSummarizer` performs real calls; local LiteRT-LM inference also exists. |
| Defer knowledge item persistence | Items and topics persist to DataStore Preferences today. |

Retained guardrails:

- Must NOT embed a WebView or ship the HTML prototype as runtime UI.
- Must NOT store or display real secrets in clear text after entry.
- Must NOT invent features outside the prototype and plan.

## Acceptance Checks (current)

- `./gradlew :shared:compileKotlinJvm :shared:jvmTest` passes on any OS.
- `./gradlew :shared:compileKotlinIosSimulatorArm64` passes on macOS.
- iOS unsigned IPA builds on the GitHub Actions macOS runner.
