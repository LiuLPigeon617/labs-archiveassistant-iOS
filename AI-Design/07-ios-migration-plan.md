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

Workflow: `.github/workflows/ios-build.yml`, running on `macos-15`.

It runs, in order: shared kernel verification on the JVM target (fast-fail before any Xcode work),
`assembleSharedKitXCFramework`, staging of the framework into `iosApp/Frameworks/`, then
`xcodebuild archive` with `CODE_SIGNING_ALLOWED=NO`, and finally zips `Payload` into an unsigned
`.ipa`. The IPA, the raw `xcodebuild` log and the shared test reports are uploaded as artifacts.

**Ordering constraint (learned the hard way):** Xcode validates a linked `.xcframework` while
planning the build graph, *before* any run-script phase executes. The framework must therefore exist
at its referenced path before `xcodebuild` starts. Building it from inside a build phase cannot
satisfy the reference and fails with `There is no XCFramework found at ...`. The workflow stages it
in a dedicated step; the in-Xcode build phase remains for developers building from Xcode, and
documents that a first build either needs the Gradle task run once or stages the framework for the
next build.

Status: the pipeline is green. It produces `JuHeShiYi-unsigned-ipa-Release` (~5.2 MB), containing
`Payload/聚合拾遗.app/` with the executable, `Assets.car`, app icons and `Info.plist`.

### Kotlin/Swift interop notes

Two rules cost several CI cycles to establish; both are documented at the call sites:

- A Kotlin `Boolean` returned from a **class member** surfaces as Swift `Bool`, but a `Boolean`
  inside a **function type** (the closures in `IosNativeBridge`) is boxed as `KotlinBoolean`.
- A Kotlin `object` is reached from Swift as `<Name>.shared`, whereas top-level functions export on a
  file facade (`<FileName>Kt`) that did not resolve reliably. The Swift-facing entry points are
  therefore an `object IosAppBridge`.

Byte payloads cross the boundary as Base64 strings rather than `ByteArray`, because Swift's
`KotlinByteArray` interop requires per-element accessors.

## Remaining work

1. Migrate the Compose UI layer into `:shared` and replace the placeholder hosted by
   `ComposeHostingViewController`.
2. Wire `LiteRT-LM` through its Swift API behind the existing `LocalLlmEngine` interface.
3. Port `ModelDownloadManager` (interface exists; the 513-line OkHttp implementation does not).
4. Implement `DocumentContentExtractor` for iOS via PDFKit (currently a `NoOp` placeholder).
5. Replace the upscaled 512 px app icon with a native 1024 px asset before any App Store submission.

