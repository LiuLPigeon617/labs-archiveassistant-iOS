# Data And State Model

> **Superseded notice:** This document described the Android-first cycle. The project has migrated to a Kotlin Multiplatform shared kernel (`:shared`) with an iOS-first app (`:iosApp`); the legacy `:app` Android app is frozen except bug fixes. See [07-ios-migration-plan.md](./07-ios-migration-plan.md). The domain/state model now lives in `:shared` commonMain and is unit-tested on JVM. Content below that is still valid is preserved; Android-specific references are annotated.

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. Data concepts come from parser input near line 251, recent topics near line 267, detail tabs near line 293, settings fields near line 318, and manage list near line 376.

Repo references:

- Domain models and state live under `shared/src/commonMain/kotlin/com/lyihub/archiveassistant/` (domain/, data/, platform/, state/), shared across Android and iOS.
- Keep pure model tests under `shared/src/jvmTest/` (`./gradlew :shared:jvmTest` runs on any OS).
- The state layer is `ArchiveAssistantStateStore` (a hand-written store using `mutableStateOf` + `CoroutineScope(Dispatchers.IO)`, no ViewModel/Hilt), being migrated; it has ~3 Android leaks to remove (Context for mock resources, Uri for document import, Log).

## Topic

`Topic` represents a user-managed collection.

Fields:

- `id: String`
- `title: String`, max 20 user-visible characters for create and rename UI.
- `iconName: String`, the symbolic topic icon key used by topic rows.
- `iconColor: String`, the display color token or hex value for the topic icon.
- `updatedAtEpochMillis: Long`, deterministic epoch millis used for sorting and display.

Responsibilities:

- Drives recent topic list and all-topic manage list.
- Provides selected topic title for Detail.
- Derives visible item counts from `KnowledgeItem` lists filtered by `topicId`; counts are not stored on `Topic`.
- Does not own raw API secrets or engine settings.

## KnowledgeItem

`KnowledgeItem` is one saved card inside a topic.

Fields:

- `id: String`
- `topicId: String`
- `contentType: ContentType`
- `title: String`
- `summary: String`
- `fullText: String`
- `sourceUrl: String?`
- `documentFormat: DocumentFormat?`
- `fileName: String?`
- `fileSize: Long?`
- `createdAtEpochMillis: Long`

Responsibilities:

- Drives card feed and card modal.
- Derives visible type labels from `contentType.label`; free-form per-item tags are not part of the model.
- Items and topics persist today (DataStore Preferences on Android; multiplatform-settings is planned for KMP). The earlier "local content only during the first implementation cycle" restriction no longer holds.
- Keeps display preview text in `summary` and full modal or detail text in `fullText`.

## ContentType

`ContentType` maps to the prototype tabs.

Values:

- `ALL`, UI label `全部`.
- `WEB_ARTICLE`, UI label `网页文章`.
- `IMAGE_SCREENSHOT`, UI label `图像截屏`.
- `DOCUMENT_PDF`, UI label `文档/PDF`.
- `PLAIN_TEXT`, UI label `文本片段`.

Filtering rule:

- `ALL` shows every `KnowledgeItem` for the selected topic.
- Other values match `KnowledgeItem.contentType` exactly.

## AiEngineSettings

`AiEngineSettings` stores editable local settings only. The current first-pass model uses `AiEngineType.CLOUD_API` and `AiEngineType.LOCAL_MODEL` as the engine selector, and persists these settings with AndroidX DataStore Preferences.

Fields:

- `engineType: AiEngineType`
- `baseUrl: String`
- `modelName: String`
- `apiKeyAlias: String`
- `localEndpoint: String`

Rules:

- Treat `apiKeyAlias` as a local reference or display alias, not a raw secret.
- Persist `engineType`, `baseUrl`, `modelName`, `apiKeyAlias`, and `localEndpoint` through typed settings keys (DataStore Preferences on Android; multiplatform-settings planned for KMP).
- Keep the raw API key entry UI-local; do not write raw API secrets to settings.
- Remote AI is implemented and does perform real network calls when a user triggers classify/summarize: OpenAI-compatible `/chat/completions`, OpenAI Responses `/responses`, Anthropic `/messages`, Gemini `:generateContent`. This supersedes the earlier "no real network validation" guardrail.
- Tests and previews use fake/mocked AI endpoints; real network calls only occur on user-triggered classify/summarize.

Knowledge content persistence is in place for `Topic` and `KnowledgeItem` (DataStore Preferences on Android). The earlier "deferred in this pass / in-memory seeded data" statement no longer holds.

## AppPane

`AppPane` describes the active high-level surface.

Values:

- `TOPICS`
- `DETAIL`
- `SETTINGS`
- `CLASSIFICATION_REVIEW`
- `CARD_DETAIL`
- `MANAGE`

`MANAGE` owns the all-topic management surface. Create, rename, and delete confirmation dialogs remain layered state over the current pane rather than separate `AppPane` values.

## Layout Mode State

`LayoutModeState` describes responsive structure.

Fields:

- `windowSizeClass: Compact | Medium | Expanded`
- `foldPosture: Flat | HalfOpen | Tabletop | Unknown`
- `hingeBounds: Rect?`
- `usesTwoPane: Boolean`

Responsibilities:

- Keeps master/detail decisions outside individual feature components.
- Gives UI components hinge-safe content bounds.

## Six Ministries Taxonomy

Topics follow the six-ministries taxonomy: 吏·名籍, 户·府库, 礼·典章, 兵·行令, 刑·稽核, 工·营造. It is an enum and IMMUTABLE at runtime. Topic create/rename/delete are rejected with the message `六部分类已固定，不能新建、重命名或删除。`. An unknown `topicId` resolves to 户·府库 (treasury). This supersedes the prototype-era `TopicManagePane` create/rename/delete flows.

## Guardrails

- Must NOT persist real secrets in plain text.
- Must NOT add remote AI response models before they are wired through the existing remote summarizer path.
- Must NOT let `KnowledgeItem` depend on Android UI classes (it must stay in commonMain).
- Must NOT rename the documented fields away from the current Kotlin domain model without updating the domain models and tests together.

## Acceptance Checks

- Unit tests can create sample `Topic`, `KnowledgeItem`, `ContentType`, `AiEngineSettings`, `AppPane`, and `LayoutModeState` values without Android framework dependencies.
- Filtering tests verify the prototype tabs plus the plain-text fallback.
- Settings tests verify API key masking and no real network validation call path.
