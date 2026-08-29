# Product Functional Analysis

> **Superseded notice.** This file described the Android-first cycle. AI classification is now real
> (remote + on-device), and the target platform is iOS. See
> [07-ios-migration-plan.md](07-ios-migration-plan.md).

`聚合拾遗` is a local-first knowledge curation app. The user gives mixed input, asks the app to
classify it, then reads collected cards under topics.

## Product Shape

- **Input**: text, pasted content, links, dragged files, clipboard content, shared documents.
- **Classify**: `智能归纳` produces a topic assignment plus title, summary, content type and
  document format.
- **Read**: collected cards under one of six fixed ministries.

## Required Functional Areas

| Area | Behavior | iOS notes |
|---|---|---|
| Parser input | One prominent input area. Accepts text, links, clipboard, and file references. | iOS has no Android-style system drag; use share sheet, Files picker, and `DropDelegate`. |
| AI classify | `智能归纳` classifies input into one of the six ministries. | Real remote calls plus optional on-device LiteRT-LM. |
| Recent topics | `最近主题` list with entry points to detail and manage. | Rendered by Compose. |
| Detail tabs | Reader with `全部`, `网页文章`, `图像截屏`, `文档/PDF` filters. | Rendered by Compose. |
| Card modal | Tapping a card opens a modal with type label, title, content, actions. | Rendered by Compose. |
| Topic manage | `全部主题` list. | **Fixed taxonomy**: create/rename/delete are intentionally disabled. |
| Settings | Engine type, Base URL, API key, model, local model controls. | **Native SwiftUI** — see below. |
| Layout adaptation | Compact single pane with back navigation; expanded master/detail. | `NavigationSplitView` on iPad; multi-window via `WindowGroup`. |

## The Six Ministries (fixed taxonomy)

`吏 · 名籍`, `户 · 府库`, `礼 · 典章`, `兵 · 行令`, `刑 · 稽核`, `工 · 营造`.

The taxonomy is an enum (`SixMinistry`) and is **immutable at runtime**. Topic create, rename, and
delete are rejected with the message `六部分类已固定，不能新建、重命名或删除。` Any unknown
`topicId` silently resolves to `户 · 府库`.

## UI Technology Split (iOS)

A deliberate decision driven by measured evidence: the Android build used exactly **five** Material
icons (two back arrows, one gear, one close, one add), and every one of them sat in navigation or
settings chrome. All distinctive visuals are hand-drawn assets: 23 memorial covers, 56 article
images, xuan-paper textures, vermilion seals, and three calligraphic fonts.

Therefore:

- **Native SwiftUI** — settings, app shell, document picker, share sheet. Inherits Dynamic Type,
  VoiceOver, keyboard navigation, `SecureField`, and multi-window for free. SF Symbols replace the
  handful of Material icons at no cost to brand identity.
- **Compose Multiplatform** — home, detail, memorial panes, dialogs, and the memorial
  fold-out reader. Preserves the app's distinctive look while keeping the shared code base.

## Module Responsibilities

- **Home module**: parser input, classify action, recent topic list.
- **Topic module**: topic selection and the fixed ministry list.
- **Reader module**: detail tabs, filtered feed, card modal.
- **Settings module**: AI engine settings editing and masking (native SwiftUI on iOS).
- **Layout module**: pane selection and compact/expanded choice.

## Guardrails (current)

- Must NOT send user content to any endpoint other than the user-configured AI engine.
- Must NOT store or display real secrets in clear text after entry.
- Must NOT log API keys, model file contents, or raw item text.
- Must NOT claim the six-ministry taxonomy is user-editable.
- AI is opt-in: the app must remain fully usable for manual archive without configuring an engine.

## Acceptance Checks

- Compose panes expose test tags for parser input, classify action, recent topics, detail tabs,
  card modal, and settings pane.
- Unit tests cover classification routing by `ContentType`.
- Instrumented tests open settings and confirm no network call happens until the user configures
  an endpoint.
