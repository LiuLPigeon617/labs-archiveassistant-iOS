# AI Engine Settings Design

> **Superseded notice:** This document described the Android-first cycle. The project has migrated to a Kotlin Multiplatform shared kernel with an iOS-first app (`:iosApp`); the legacy `:app` Android app is frozen except bug fixes. See [07-ios-migration-plan.md](./07-ios-migration-plan.md). The "local-only / no network" guardrails below were false and are corrected: remote and on-device AI are implemented. Settings on iOS are native SwiftUI (Form, Picker, SecureField, SF Symbols). Content below that is still valid is preserved.

Source prototype: local high-fidelity prototype `knowledge-curation-app-11.html`. Settings begin near line 308 with `配置应用设置`, engine controls near line 318, API mode near line 337, and local model fields near line 356.

Repo references:

- Settings UI lives in the iOS app as native SwiftUI (`Form`, `Picker`, `SecureField`, SF Symbols) under `:iosApp`; the legacy Android settings lived under `app/src/main/java/com/lyihub/archiveassistant/` shown from the Compose shell in `MainActivity.kt`.
- Settings state is driven by the shared kernel (`:shared`); on Android it used DataStore Preferences (multiplatform-settings is planned for KMP).
- Tests use `:shared` JVM tests for masking rules and `:iosApp` SwiftUI preview/unit tests for interaction; legacy Android used `app/src/test` and `app/src/androidTest`.

## Local-Only Settings

> Superseded: settings are no longer "local UI state only". Remote AI is implemented and real network calls happen on user-triggered classify/summarize.

Cloud mode fields:

- Engine type: `API`.
- Base URL: editable text field, default may be blank or a harmless placeholder.
- API key: secret entry field with masked display.
- Model name: editable text field, for example a non-secret sample model label.

Local mode fields:

- Engine type: `本地模型`.
- Local model: on-device LiteRT-LM inference via `LocalLlmEngine` (initialize/generate/benchmark/release; NPU/GPU/CPU backends with automatic fallback), model `GEMMA_4_E4B_IT` (Gemma 4 E4B, ~3.66 GB, SHA-256 verified, downloaded from ModelScope). The prototype-era labels `Qwen3-2B` and `Gemma 3 4B` are superseded by the shipped `GEMMA_4_E4B_IT`.
- Helper text may note that local model performance depends on device hardware.

## API Key Masking

- Mask the key while typing where platform controls allow it.
- After entry, display only a masked value such as `••••••••1234` or a plain ASCII equivalent in tests.
- Never write real keys to logs, previews, fixtures, screenshots, docs, or test output.
- Clearing the key should remove both raw and masked values.

## No Network Validation

> Superseded: settings no longer forbid network access. Remote AI is implemented — OpenAI-compatible `/chat/completions`, OpenAI Responses `/responses`, Anthropic `/messages`, Gemini `:generateContent` — and an `AiEndpointLatencyTester` performs real endpoint probes. What remains guarded is key handling and response parsing.

- The settings screen itself must not call the Base URL on mere navigation or save; network calls are user-initiated.
- The save action updates settings state.
- Validation is limited to local field shape, such as empty string handling, if implemented.
- Remote responses are forced to strict JSON output; shared `extractJsonObject()` tolerates Markdown fences and surrounding prose.
- API keys must still be masked and never written to logs, previews, fixtures, screenshots, docs, or test output.

## Guardrails

- Must NOT add OkHttp/HttpURLConnection or hand-rolled `org.json`; use Ktor Client (Darwin engine on iOS, OkHttp on Android/JVM) and kotlinx-serialization.
- Must NOT make real AI API calls or key validation calls from the settings screen on mere navigation or save; calls are user-initiated.
- Must NOT include real secrets or API keys in logs, previews, fixtures, screenshots, docs, or test output.
- Must NOT claim cloud mode is functional without a valid configured endpoint; remote AI is real and does make network calls.

## Acceptance Checks

- Unit tests verify API key masking, clearing, and cloud/local mode state transitions.
- Instrumented tests switch between `API` and `本地模型` and confirm the expected fields appear.
- A repo search confirms the Ktor + kotlinx-serialization stack is in place and that settings-driven network calls are user-initiated rather than fired on navigation/save.
