# iOS Migration Plan

**Status: in progress.** This is the authoritative plan for migrating `聚合拾遗` from an
Android-only Compose app to an iOS-first app (iPhone + iPad) built on a Kotlin Multiplatform
shared kernel.

## Why KMP and not a full rewrite

Measured against the Android sources (60 main files, ~17.1k lines):

| Category | Lines | Share |
|---|---|---|
| Direct reuse (pure Kotlin/Compose: domain, remote AI, prompts, Compose components, memorial geometry) | ~7,800 | 46% |
| Reuse after swapping libraries (data layer: Ktor, Ksoup, PDFKit, kotlinx-io, multiplatform-settings) | ~2,700 | 16% |
| Must be rewritten (Android service, memorial canvas views, entry points, settings UI) | ~7,800 | 46% |

Shares exceed 100% because ~310 lines of memorial geometry inside the "rewrite" bucket are counted
in "direct reuse". Net: roughly 40% rewrite, 60% port-and-adapt.

A rewrite (e.g. Rust + Vue3 via Tauri) would reuse 0% and would move the memorial reader — already
the app's worst-performing surface per the README — into a WKWebView.

## Architecture

```
iosApp/                      Swift sources + Xcode project (macOS only)
  ArchiveAssistantApp.swift        @main, WindowGroup (iPad multi-window)
  MainWorkspaceView.swift          NavigationSplitView shell
  SettingsView.swift               native SwiftUI: Form, Picker, SecureField, SF Symbols
  ComposeHostingViewController     UIViewControllerRepresentable bridge
  SharedStateBridge.swift          ObservableObject over the Kotlin store

shared/                      KMP module
  commonMain/
    domain/       SixMinistry taxonomy, items, AI contracts, prompts, JSON extraction
    data/         Ktor transport, provider request/response shapes
    platform/     expect/actual seams: PlatformFileStore, Logger, ContentSource,
                  BundledAssetReader
    state/        ArchiveAssistantStateStore (migration in progress)
  androidMain/    OkHttp engine, res/raw assets, Context.filesDir
  iosMain/        Darwin engine, NSBundle assets, Application Support, Swift bridge
  jvmMain/        compile-verification and unit tests on Windows/Linux

app/                         legacy Android Compose app (frozen)
```

### Platform seams

The Android `state` layer leaked `Context`, `Uri`, and `Log`. These are now interfaces in
`platform/PlatformServices.kt`:

| Replaces | Abstraction |
|---|---|
| `Context.filesDir` | `PlatformFileStore` (`itemsDir`, `modelsDir`, `exists`, `writeBytes`, `readBytes`) |
| `android.util.Log` | `Logger` (`d`, `w`, `e`) |
| `android.net.Uri` + ContentResolver | `ContentSource` (`displayName`, `openRead`) |
| `res/raw` + `openRawResource` | `BundledAssetReader.materialize(assetName, outputFileName)` |

## Library swaps

| Was | Now | Reason |
|---|---|---|
| `HttpURLConnection` / OkHttp | Ktor Client (Darwin on iOS, OkHttp on Android/JVM) | KMP, native TLS per platform |
| org.json | kotlinx-serialization | KMP; typed requests, tolerant response parsing |
| Jsoup | Ksoup | Jsoup is JVM-only |
| PDFBox Android | PDFKit on iOS, PDFBox retained on Android | system PDF stack per platform |
| `java.util.zip` (hand-rolled DOCX) | kotlinx-io | JVM-only API |
| DataStore Preferences + hand-written JSON codec | multiplatform-settings (planned) | removes ~390 lines of codec |

Deliberately **not** introduced: Koin/DI (constructor defaults already give 30 test files good
seams), Voyager/Decompose (`AppPane` enum + `when` is sufficient until deep links are needed).

## Execution order

1. ✅ KMP skeleton + domain migration.
2. ✅ Remote AI migration to Ktor + kotlinx-serialization (15/15 tests green).
3. ✅ Platform seams defined; iOS app shell and native SwiftUI settings written.
4. ⏳ **Migrate `state` layer** onto the platform seams.
5. ⏳ Compose Multiplatform UI: home, detail, dialogs, then the memorial reader as a
   standalone refactor with a performance budget.
6. ⏳ LiteRT-LM Swift adapter via the official iOS Swift API.
7. ⏳ GitHub Actions: unsigned IPA build with full log output.

## Build and verification

| Command | Where | Purpose |
|---|---|---|
| `./gradlew :shared:compileKotlinJvm :shared:jvmTest` | any OS | verifies the shared kernel |
| `./gradlew :shared:compileKotlinIosSimulatorArm64` | macOS | verifies the iOS target |
| `./gradlew :shared:assembleSharedKitXCFramework` | macOS | produces the framework Xcode links |
| Xcode build of `iosApp` | macOS | the only way to produce an IPA |

iOS compilation is impossible on Windows: Kotlin/Native needs the Apple SDK, and app signing
requires Xcode.

## Known interop constraint

`ComposeUIViewController` does not provide a reliable intrinsic content size to SwiftUI. Without an
explicit size the Compose subtree can collapse to zero height. `ComposeHostingViewController`
therefore returns a concrete size from `sizeThatFits`, and the Compose view is pinned with explicit
Auto Layout constraints. Do not rely on self-sizing.

## Signing and CI

CI produces an **unsigned** IPA for verification only. Unsigned artifacts cannot be installed on
physical devices without a signing step; distribution requires an Apple Developer certificate
configured as repository secrets.
