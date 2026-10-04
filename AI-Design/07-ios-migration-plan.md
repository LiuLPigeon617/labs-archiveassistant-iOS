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
4. ✅ **`state` layer migrated** onto the platform seams (commit `40fc540`).
5. ⏳ Compose Multiplatform UI: home, detail, dialogs, then the memorial reader as a
   standalone refactor with a performance budget. Theme, components, layout and the first portable
   screens are done; `SettingsPane` landed together with its two data dependencies.
6. ⏳ LiteRT-LM Swift adapter via the official iOS Swift API.
7. ✅ GitHub Actions: unsigned IPA build with full log output (pipeline green).

## Build and verification

| Command | Where | Purpose |
|---|---|---|
| `./gradlew :shared:compileKotlinDesktop :shared:desktopTest` | any OS | verifies the shared kernel |
| `./gradlew :shared:compileKotlinIosSimulatorArm64` | macOS | verifies the iOS target |
| `./gradlew :shared:assembleSharedKitXCFramework` | macOS | produces the framework Xcode links |
| Xcode build of `iosApp` | macOS | the only way to produce an IPA |

> Earlier revisions of this table said `compileKotlinJvm`/`jvmTest`. Those tasks do not exist: the
> verification target is the *named* `jvm("desktop")` target, so the tasks are `…Desktop`.

iOS compilation is impossible on Windows: Kotlin/Native needs the Apple SDK, and app signing
requires Xcode.

### Gradle needs write access outside the workspace

Two failures that look like build breakage but are sandbox artifacts:

- `FileNotFoundException: …\gradle-8.13-bin.zip.lck (access denied)` — the wrapper always touches its
  lock file under `%USERPROFILE%\.gradle`, even when `-g` points elsewhere.
- `Failed to load native library 'native-platform.dll'` — Gradle extracts its native services into
  the same directory.

Both disappear once Gradle may write to the Gradle home. A read-only session cannot verify the
kernel at all, so treat a green `:shared:desktopTest` as a precondition for committing shared work.

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

Status: the pipeline is green, and it has real green runs behind it. **Run 37214783385 on `4b0547c`**
is the strongest one: both jobs — `Build unsigned IPA` and `Simulator smoke (screenshots)` — concluded
`success`, and the simulator produced three screenshots of the app actually rendering the shared Compose
settings pane. An earlier milestone was **run 37199938026 on `135d2f2`**, the first run whose Compose
root compiled and archived (15 steps plus 4 post steps, 11 minutes). Before `10a44a8` the workflow had
**never** passed.

The workflow has a **second job**, `Simulator smoke (screenshots)`, which runs in parallel and does
not gate the IPA. It exists because a green archive build proved nothing about what the app draws; see
the section below. Note that the parallel job roughly doubles the runner time of the workflow.

Artifacts from run 37214783385: `JuHeShiYi-unsigned-ipa-Release` **23,392,233 B (~22.3 MB)**,
`ios-simulator-smoke` 4,744,378 B, `xcodebuild-log` 14,009 B, `simulator-xcodebuild-log` 14,333 B,
`shared-test-results` 6,397 B. The IPA contains `Payload/聚合拾遗.app/` with the executable,
`Assets.car`, app icons, `Info.plist`, and — only since `4b0547c` — a populated `compose-resources/`.

The IPA size is worth watching now that the assets are bundled, but the attribution across these runs is
**not** what it first looked like:

| Run | Commit | IPA |
|---|---|---|
| 37195349460 | `10a44a8` | 5,650,070 B (~5.4 MB) |
| 37199938026 | `135d2f2` | 14,170,223 B (~13.5 MB) |
| 37214783385 | `4b0547c` | 23,392,233 B (~22.3 MB) |

The first delta (+8.52 MB) was originally recorded here as "the fonts", and that was wrong: the fonts
were not in the bundle at all, because the Compose resource sync task never ran (see the simulator smoke
section). That 8.52 MB is the cost of linking the Compose runtime — UIKit/Skia and its own resources —
which is what made the root component work. The second delta (+9.22 MB) is the fonts and drawables
arriving for real: 12,917,432 bytes of TTF plus ~1.48 MB of drawables, compressed inside
`compose-resources`. The conclusion "the fonts dominate the IPA" only holds from `4b0547c` onward.

Unlike the JPEGs, the TTFs are already internally compressed, so zip gains almost nothing on them:
re-encoding art will not recover this. If bundle size becomes a release concern, subsetting the two
faces is the lever (see the font section).

### The simulator smoke test: closing the "verified by compile only" gap

Everything above proves the app **builds** for iOS. None of it proves anything about what the app
**draws** — and until this job existed, the shared Compose UI, the calligraphic fonts and the imperial
palette had only ever been verified by a green compile. A compile cannot tell you whether
`archiveFontFamily` returned the real face or fell back to `FontFamily.Serif`, nor whether
`ArchiveAssistantTheme` applied the palette at all.

`.github/workflows/ios-build.yml` now has a second job, `Simulator smoke (screenshots)`, which runs
**in parallel** with the IPA job (no `needs:`, so one failing does not mask the other). It builds the
app for the simulator, installs it, launches it three times, and screenshots each launch — all driven
by `.github/scripts/simulator-smoke.sh`.

Three screenshots, because they answer different questions:

| Screenshot | Launch | Question it answers |
|---|---|---|
| `01-native-shell-light.png` | no argument | does the SwiftUI shell render at all (the baseline) |
| `02-compose-settings-light.png` | `--compose-preview` | does the shared Compose tree render, and do the bundled fonts show |
| `03-compose-settings-dark.png` | `--compose-preview`, dark appearance | did `isSystemInDarkTheme()` resolve to the dark scheme at first composition |

All three upload in the `ios-simulator-smoke` artifact. That artifact is the point: it answers the
font question by *showing* it, which is the only way that question can be answered.

**Two deliberate build choices.** The simulator build runs `-sdk iphonesimulator -configuration Debug`
with `ARCHS=arm64` and **without** the signing overrides the archive build needs — the simulator SDK
expects an ad-hoc signature and `simctl install` accepts the default product, so
`CODE_SIGNING_ALLOWED=NO` would only risk producing a bundle the simulator refuses. And the dark
screenshot relaunches under a switched appearance rather than toggling it under a running app, because
that is what exercises the value `isSystemInDarkTheme()` resolves at *first* composition.

**The `--compose-preview` launch argument.** `simctl launch` can pass arguments but cannot tap the
toolbar button that opens `ComposeRootPreviewView`, and driving the UI instead would mean adding a UI
automation target to the Xcode project. So `MainWorkspaceView.swift` reads
`ProcessInfo.processInfo.arguments` and starts with the preview already presented. It changes only
where the app *starts*, not what it renders. The manual equivalent, useful locally on a Mac:

```bash
xcrun simctl launch booted com.lyihub.archiveassistant --compose-preview
```

**What the pixel check does and does not prove.** `.github/scripts/check-screenshot.swift` decodes each
PNG, quantises colours to 5 bits per channel and fails if fewer than 25 distinct colours appear. The
threshold is calibrated against real captures rather than guessed — measured with the same sampling and
quantisation the script uses, on run 37203635522 (where every Compose launch crashed) and run 37214783385
(the first run where Compose actually rendered):

| capture | distinct colours | source |
|---|---|---|
| genuinely blank screen | 1–3 | — |
| native SwiftUI shell (real content: title, two toolbar buttons, placeholder) | **86** | both runs |
| Compose settings, light | **167** | 37214783385 |
| Compose settings, dark | **154** | 37214783385 |
| springboard fallback after a crash | **2663 / 2822** | 37203635522 |

The first threshold was 50, which put legitimate content only 36 shades from failing; 25 leaves the
sparse native shell comfortable room while still sitting far above a blank screen. Note the last row: a
crashed app's screenshot is *the most colourful image of the five*, so this check cannot detect the
failure that matters most. It catches exactly one thing — a blank, flat or unmounted screen — and will
happily pass a screen that is wrong in every other way. Read the images; the check exists so that an
empty screen fails the build instead of being uploaded and quietly ignored.

This table was first written from the crashed run, where I mislabelled both springboard captures as
Compose screens; the real Compose tree is *sparse* (154–167 colours), not colour-rich. The correction
matters because it inverts the intuition the numbers were meant to support: rich colour is the signature
of the failure, not of success.

**The trap this job is built around.** A failed launch leaves the screenshot showing the simulator
home screen — colourful, full of icons, and passing every pixel check. So the script never trusts a
screenshot on its own. Per phase it: asserts the app is actually running (`assert_alive`, polling
`simctl spawn ... launchctl list`), captures, stops, and then fails on any `.ips` crash report newer
than the run marker, printing the console log tail so the cause is visible in the CI log instead of
only inside the artifact. The launch check accepts two signals, because neither is documented as
guaranteed (`launchctl list` and the `bundle: <pid>` line), but the liveness check before a capture
accepts only `launchctl list`: `simctl` prints the pid line when the launch is *requested*, not while
the process is alive, so a crashed app still produces it.

**First real find (run 37203635522): the app died on launch, and the screenshots were the home screen.**
Both Compose launches aborted about two seconds in. The `.ips` report said only `EXC_CRASH` /
`SIGABRT` with an empty `exceptionReason`; the actual message was in the launch console:

```
Uncaught Kotlin exception: kotlin.IllegalStateException: Error: `Info.plist` doesn't have a valid `CADisableMinimumFrameDurationOnPhone` entry, or has it set to `false`.
This will result in an inadequate performance on iPhones with high refresh rate.
Add `<key>CADisableMinimumFrameDurationOnPhone</key><true/>` entry to the `Info.plist` file to fix this error.
To disable this check, set `ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) { .. }`.
```

with `kfun:androidx.compose.ui.uikit.PlistSanityCheck.PlistSanityCheck$performIfNeeded$1.invoke#internal`
on the faulting thread. Compose Multiplatform 1.8.0 (`gradle/libs.versions.toml:23`) enforces this from
`PlistSanityCheck.uikit.kt`, and the check fires from a dispatch block at process start rather than from
the hosted controller — so the plain SwiftUI shell launch was unaffected, while every launch that
reached Compose died.

Two things follow, and both generalise:

- The fix belongs in `iosApp/iosApp/Info.plist`, not in code. The entry is a real requirement: without
  it iOS caps the app at 60 Hz on ProMotion iPhones, so `enforceStrictPlistSanityCheck = false` would
  silence the crash *and* keep the performance loss. The key is now in the plist; if it ever disappears,
  this job fails.
- The screenshots that were captured were the springboard. `02-compose-settings-light.png` is 3.58 MB of
  wallpaper and app icons — a large, colourful image that `check-screenshot.swift` accepts without
  complaint. That is precisely why crash detection, not the pixel check, is what makes this artifact
  trustworthy: the pixel check proves a screen is not blank and nothing more, and a crashed app is not
  blank.

**Second real find (run 37211879390), and this one was shipping: the app bundle had no `compose-resources`.**
Once the plist key was in place the app finally reached Compose and aborted there instead:

```
Uncaught Kotlin exception: org.jetbrains.compose.resources.MissingResourceException:
Missing resource with path: .../聚合拾遗.app/compose-resources/
  juheshiyi.shared.generated.resources/font/san_ji_xing_kai_jian_ti_cu.ttf
    at ... kfun:com.lyihub.archiveassistant.ui#archiveFontFamily(...)
    at ... kfun:com.lyihub.archiveassistant.ui#ArchiveAssistantRoot(...)
```

Compose Multiplatform does **not** put its resources inside the framework. It copies them into the app
bundle with `SyncComposeResourcesForIosTask`, and the plugin wires that task to
`embedAndSign<Framework>AppleFrameworkForXcode`. This project stages the XCFramework by hand in a build
phase and therefore never runs that task: the framework compiled and linked correctly, and the fonts were
simply absent at runtime.

That is why this is more than a test artifact. **The 5.4 MB unsigned IPA the other CI job had been
producing since the Compose root landed would have aborted on launch on a real device in exactly the same
way.** Nothing in the pipeline could see it: a missing runtime resource is invisible to the compiler, to
the linker, and to `codesign`, and the archive step only ever checked that an `.app` and an IPA came out.
The screenshots are what forced the issue into the open.

The fix runs the sync from the "Build SharedKit.xcframework" build phase — it has to be a build phase and
not a CI step before `xcodebuild`, because the task resolves its output location from Xcode's
`BUILT_PRODUCTS_DIR` and `UNLOCALIZED_RESOURCES_FOLDER_PATH`. The task name is derived from the framework
classifier (`sync${getClassifier()}ComposeResourcesForIos`), so the phase discovers it via
`./gradlew :shared:tasks --all` instead of hardcoding a name that would silently rot, and fails if the
directory is still empty afterwards.

Both jobs now assert the two launch preconditions on the **built product**, so this class of regression
fails the build rather than shipping: the simulator job checks its `.app`, and the IPA job checks the
`Payload` app that goes into the archive — at least one `.ttf` under `compose-resources`, plus the plist
key. Checking the artifact people actually install matters more than checking the one under test.

**First green run with the Compose tree actually on screen (run 37214783385, commit `4b0547c`).** Both
jobs pass. The simulator job reports 3 screenshots, 0 crash reports, and the images are the first real
evidence of what the shared UI looks like on iOS: the settings pane renders with the Sanxingdui
calligraphic title font, the xuan-paper background texture, and the cinnabar/terracotta action button.
The font question that motivated this whole path is answered — `archiveFontFamily` resolves and the
typeface paints, rather than silently falling back to a system serif.

Two numbers from this run correct earlier claims in this document:

| build | state | IPA bytes |
|---|---|---|
| `10a44a8` | before the Compose root | 5,650,070 |
| `135d2f2` | Compose root wired in | 14,170,223 |
| `4b0547c` | `compose-resources` actually shipped | 23,392,233 |

The first jump (+8.52 MB) was attributed to the fonts at the time, and that was wrong: the fonts were not
in the bundle at all until `4b0547c`. That 8.52 MB is the cost of linking the Compose runtime
(UIKit/Skia and its own resources) into the app. The second jump (+9.22 MB) is what the fonts and
drawables actually cost — 12,917,432 bytes of TTF plus ~1.48 MB of drawables, compressed inside
`compose-resources`. So "the fonts dominate the IPA" only becomes true once they are genuinely packaged,
which is exactly what this section had to fix first.

**Not covered yet:** iPad, landscape, and any interaction past first presentation. The workflow builds
for `TARGETED_DEVICE_FAMILY = "1,2"` but the script boots an iPhone runtime only, so the iPad layout
— the reason `NavigationSplitView` and the orientation forwarding in the hosting controller exist — is
still unverified. A screenshot of the settings pane is also not a test that the settings *work*.

### Kotlin/Swift interop notes

Two rules cost several CI cycles to establish; both are documented at the call sites:

- A Kotlin `Boolean` returned from a **class member** surfaces as Swift `Bool`, but a `Boolean`
  inside a **function type** (the closures in `IosNativeBridge`) is boxed as `KotlinBoolean`.
- A Kotlin `object` is reached from Swift as `<Name>.shared`, whereas top-level functions export on a
  file facade (`<FileName>Kt`) that did not resolve reliably. The Swift-facing entry points are
  therefore an `object IosAppBridge`.

Byte payloads cross the boundary as Base64 strings rather than `ByteArray`, because Swift's
`KotlinByteArray` interop requires per-element accessors.

## Compose UI migration status

### Done (compiles green)

`./gradlew :shared:compileKotlinDesktop :shared:desktopTest` passes. 23 files now live in
`shared/src/commonMain/.../ui/`:

- `theme/`: Color, ImperialPalette, ImperialFonts
- `components/`: ActionButton, ArchiveChip, ArchiveDialog, ArchiveNoticeBanner, HeaderBackButton,
  PaneContainer, PaneHeader, XuanPaperBackground
- `layout/`: LayoutMode
- `screens/`: ArchiveVisuals, MemorialCoverSequence, MemorialStackGeometry, PaneHeroHeader,
  SettingsPane, TagVisuals, TopicManagementDialogs
- root: ArchivePainter, ArchiveFont (the two expect/actual seams)

### Verification target

The compile-verification target is `jvm("desktop")`, not plain `jvm()`. Compose UI artifacts do not
resolve for a plain `jvm()` target, which previously meant every UI change needed a ~20 minute macOS
CI round trip. `jvm("desktop")` compiles Compose on Windows in seconds.

Note the Kotlin DSL requires explicit accessors for a named target's source sets:

```kotlin
val desktopMain by getting
val desktopTest by getting
```

Source directories are `shared/src/desktopMain` and `shared/src/desktopTest`.

**What this target cannot verify — read before trusting a green desktop build.** The desktop target
shares `commonMain` sources with iOS, but it does **not** share the same API surface or the same
compiler. Two consequences:

1. **iOS targets are not even configured off macOS.** `shared/build.gradle.kts:21` is
   `if (isMacOs()) { … }` around `listOf(iosArm64(), iosSimulatorArm64())`, with `isMacOs()` at
   line 103. On Windows `:shared:tasks --all` lists no `compileKotlinIos*` task at all, so there is
   no local command that compiles this code the way iOS will.
2. **A JVM-only dependency can resolve on desktop and fail on iOS.** Kotlin/Native has a different
   artifact for every library, and packages that exist in the JVM variant may be absent from the
   native one.

This produced a real failure, caught only by CI:

```
e: shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/components/XuanPaperBackground.kt:14:28 Unresolved reference 'res'.
> Task :shared:compileKotlinIosArm64 FAILED
```

Line 14 was `import androidx.compose.ui.res.painterResource`, dead code copied from the Android
source (the file goes through `archivePainter`). It resolved on desktop because
`androidx.compose.ui.res` ships in the JVM artifact; package `androidx.compose.ui.res` does not
exist for Kotlin/Native, so the iOS compile could not resolve the import. Fixed in `cb8e6a8`.

Practical rule: **when a `commonMain` file is ported from Android, strip the `androidx.compose.ui.res`
import block even if the build is green**, and treat the macOS CI run as the only authoritative
iOS compile. Grepping `androidx\.compose\.ui\.res|LocalContext|^import android\.` under
`shared/src/commonMain` is a cheap pre-flight check; the only remaining hits live in `androidMain`
(`platform/Platform.kt`, `data/Support.kt`), which iOS never compiles.

### Remaining portable screens, and the strategy required

Still in `:app`: `HomePane.kt` (1493 lines) and `MemorialBriefingPane.kt` (805).

They were attempted and reverted, because they thread Android `R.drawable` **`Int` resource ids**
through many private helper composables. Converting them incrementally leaves the module
uncompilable and produces half-converted files. `MemorialBriefingPane` is the smaller of the two and
the better next target: 10 `painterResource` sites, one `List<Int>` cover list, two legacy fonts.

**Required strategy — do this as one atomic pass per file:**

1. First convert **every** resource parameter from `Int` to `String` across all helper signatures
   *and* their call sites in that file (`imageRes` -> `imageAsset`, `ornamentRes` -> `ornamentAsset`,
   `backgroundRes` -> `backgroundAsset`, `MemorialCoverResources` -> `MemorialCoverAssets`, and plain
   `List<Int>` cover lists -> `List<String>`).
2. Replace `painterResource(id = X)` with `archivePainter(X)`, wrapping call sites that need a
   non-null `Painter` in `archivePainterOrPlaceholder(X)`.
3. Replace `ImperialTitleFont` / `ImperialDisplayFont` / `ImperialStampTitleFont` with
   `LocalImperialFonts.current.title` / `.display`.
4. Only then copy the file into `shared/commonMain`.

Do not compile between steps 1–4 for a given file; the intermediate states are not valid.

Three more conversions the atomic pass must also cover, each learned while migrating `SettingsPane`:

- **Material Icons.** `Icons.Default.*` / `Icons.AutoMirrored.Filled.*` do not exist in `:shared`
  (see the icon policy below). Either draw the glyph locally — `HeaderBackButton` is the worked
  example, replacing `Icons.AutoMirrored.Filled.ArrowBack` — or take it as an injected slot.
- **`String.format` and `java.util.Locale`.** `"%.1f".format(x)` is JVM-only. `SettingsPane` had
  three such call sites; they now use deterministic rounding helpers next to the pane.
- **`rememberCoroutineScope()`.** Prefer a keyed `LaunchedEffect` over the state it acts on, which
  also cancels a stale in-flight request and keeps the pane free of a platform scope.

### Not portable by conversion: `MemorialDemoOverlay.kt` and `DetailPane.kt`

Earlier revisions listed both of these as screens to be converted with the atomic pass. Neither can
be.

`MemorialDemoOverlay.kt` (304 lines): both of its composables are thin wrappers whose entire payload
is `AndroidView { MemorialFoldView(context) }`, plus `androidx.activity.compose.BackHandler` and
`MemorialImmersiveSystemUi`. Nothing in the file draws Compose UI of its own, so its fate is the
memorial reader's (see remaining work #3). A mechanical port would produce a file that compiles
nowhere.

`DetailPane.kt` (1220 lines): it is not a resource-id problem, it is an image-and-file platform
problem. It pulls in `LocalContext`, `BitmapFactory`, `Uri`, `rememberLauncherForActivityResult`,
`copyUriToFile`, and a `Context`-based `writeMarkdownPrefillFile` — four separate Android seams. It
needs real platform abstractions (image decoding, a file/document picker, content-source
materialization) before any conversion, so it is a work item of its own rather than a fifth atomic
pass.

Net: **two** screens remain convertible, not four. That is a scope reduction of ~1500 lines against
what this document previously implied.

### `SettingsPane` migrated (compiles green)

`SettingsPane.kt` (775 lines) now lives in `shared/src/commonMain/.../ui/screens/`, together with the
two data dependencies it needed, which were still Android-only:

| File | Was | Now |
|---|---|---|
| `AiEndpointLatencyTester.kt` | `HttpURLConnection` + `System.nanoTime()` | Ktor transport behind the same `AiLatencyTransport` seam; `TimeSource.Monotonic` |
| `AiEnginePresetPreferences.kt` | DataStore `Preferences`/`MutablePreferences` | same hand-written JSON wire format, but taking and returning the raw string |
| `ui/components/HeaderBackButton.kt` | `Icons.AutoMirrored.Filled.ArrowBack` | locally drawn glyph |

Notes worth keeping:

- `AiEnginePreset` in `:shared` has **no** `localEndpoint` field — the Android model carried a
  deprecated one. The local-model latency path therefore derives its endpoint from `baseUrl` when
  that already looks like loopback, and otherwise falls back to the historical Ollama default.
- The `withContext(Dispatchers.IO)` in the latency tester became `Dispatchers.Default`: `IO` does not
  exist in common code.
- `AiEnginePresetPreferences.decode` now wraps the parser in `runCatching`. The Android version let
  `require` throw, and with no DataStore type to signal corruption it is better to degrade to an
  empty preset list.

### Platform seams introduced

| Seam | Android | iOS / desktop |
|---|---|---|
| `archivePainter(name): Painter?` | commonMain: `painterResource(Res.drawable.*)` | same code on every target |
| `archivePainterOrPlaceholder(name): Painter` | same, falling back to a transparent painter | transparent painter of the requested size |
| `archiveFontFamily(name): FontFamily?` | commonMain: `FontFamily(Font(Res.font.*))` | same code on every target |

`archivePainter` and `archiveFontFamily` are **no longer expect/actual**. Each is one `commonMain`
function resolving names through the Compose Multiplatform resource pack in
`src/commonMain/composeResources/{drawable,font}`, so the same art and type reach every target. The
per-platform `ArchivePainter.<target>.kt` and `ArchiveFont.<target>.kt` files are deleted; the Android
paint actual's `getIdentifier` path could never have worked anyway, because `:shared` cannot see
`:app`'s resource namespace.

`bundledDrawable(name)` in `archivePainter`'s file is the single source of truth for which names
resolve. Adding an asset is a two-step change: copy the file into `composeResources/drawable`, then
add its `when` branch. Names with no branch return `null`, which is how callers degrade.

**Every asset the migrated shared UI actually references is now bundled.** Only two names were still
outstanding, and both are in: `home_search_tile` (SettingsPane) and `memorial_xuan_paper`
(XuanPaperBackground). The pack holds 12 files / 1.48 MB. `home_search_tile` was resampled from
5400x3600 (13.5 MB) to 1620x1080 (464 KB, ~3.4%) — it is drawn `ContentScale.Crop` as a paper texture
under a panel, so the original resolution was ~3.3x beyond what any iPad renders, and 1620 px keeps a
30% linear sample of the source. If it ever looks soft on a large display, 2160x1440 costs 864 KB.

### The calligraphic fonts are bundled but still unused — and that exposes a bigger gap

Two of the three TTFs moved into `commonMain/composeResources/font` (+12.9 MB):

| Font | Glyphs | Used by |
|---|---|---|
| `san_ji_xing_kai_jian_ti_cu.ttf` | 4,864 | `ImperialTitleFont` in `:app`'s Type.kt |
| `dinglie_song_typeface.ttf` | 2,816 | `ImperialDisplayFont` in `:app`'s Type.kt |

`ma_shan_zheng_regular.ttf` (3,584 glyphs, 5.6 MB) was **not** copied: no code references it. None of
the three is a subset — they are full CJK faces, so the size is glyph count, not a packaging mistake.
Subsetting is the lever if the bundle size matters later.

**Bundling them changed nothing by itself**, and the reason was worth stating plainly: nothing called
`archiveFontFamily`, and nothing called `ProvideImperialFonts`. `LocalImperialFonts` held its default:

```kotlin
staticCompositionLocalOf { ImperialFonts(title = FontFamily.Serif, display = FontFamily.Serif) }
```

So the imperial typography had never been installed on **any** platform, including Android — every
pane rendered in the platform serif fallback while the fonts sat unused.

**Resolved** by the Compose root added in `be21549`: `ArchiveAssistantRoot` now installs
`ProvideImperialFonts` with both faces, so this is the first composition root that gives them a
chance to render. The Material theme gap that the root initially left open was closed right after it,
in `f402b86` (see below), so settings chrome is now imperial-coloured rather than baseline-purple.

Fonts reach composables through `LocalImperialFonts` (a CompositionLocal) rather than parameters, so
private helpers do not each need a font argument. `ProvideImperialFonts` installs them.

One API constraint to remember when editing that root: `archiveFontFamily` is `@Composable` (the
Compose resource API loads the face through composition), so resolving the faces inside
`remember { … }` fails to compile — `@Composable invocations can only happen from the context of a
@Composable function`. Keep the calls inline in the composable body.

Material Icons are deliberately **not** a dependency of `:shared`; the iOS chrome uses SF Symbols.
Icon affordances are injected slots (`backIcon`, `settingsIcon`) or drawn locally (see
`CloseSearchGlyph` in HomePane).

### The Material theme gap — found, and closed in `f402b86`

A gap surfaced while building the root: grepping the module for `MaterialTheme(` / `colorScheme =` /
`lightColorScheme` returned **nothing**. The only `ArchiveAssistantTheme` lived in `:app` at
`app/src/main/java/com/lyihub/archiveassistant/ui/theme/Theme.kt:62`, so every
`MaterialTheme.colorScheme.*` read in shared UI resolved to the Material 3 **baseline** scheme:

| Call site | Reads |
|---|---|
| `shared/.../ui/screens/SettingsPane.kt:349,364,403,534,545,587,588` | `.primary`, `.error`, `.onSurfaceVariant` |
| `shared/.../ui/components/PaneContainer.kt:33` | `.outlineVariant` |

`SettingsPane` therefore rendered with the default purple accent, not `ImperialCinnabar`, while the
imperial colours that *did* apply were only the ones referenced directly as literals
(`shared/.../ui/theme/ImperialPalette.kt`: `ImperialParchment` 0xFFE6D7BE, `ImperialBronze`
0xFFD1A36B, `ImperialLightGold` 0xFFEDD8AA, `ImperialIvory` 0xFFFCFBF6, `ImperialUmber` 0xFF8B654A,
`ImperialCinnabar` 0xFFE65D3F).

The fix is `shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/theme/Theme.kt`, ported from
the `:app` version. What the port had to decide, and why:

- **Both schemes, not just light.** The dark tokens (`DarkTerracotta` and the rest in
  `shared/.../ui/theme/Color.kt`) were already sitting next to the light ones; porting only light
  would have silently changed dark-mode appearance relative to the Android app. `darkTheme` defaults
  to `isSystemInDarkTheme()`, as on Android.
- **The whole type scale, not only the levels in use.** `archiveTypography()` defines all 13 levels.
  A partial `Typography` would leave the untouched levels on the platform default face, which reads
  as an oversight rather than a decision. The levels the shared UI reaches for today are in
  `SettingsPane.kt` (17 sites), `PaneHeader.kt:47,56`, `PaneHeroHeader.kt:50`,
  `TopicManagementDialogs.kt:104,110,144`, `ArchiveDialog.kt:67,104`, `ArchiveChip.kt:33`,
  `ActionButton.kt:42,71`, `ArchiveNoticeBanner.kt:54`.
- **Two Android behaviours deliberately not ported**, with reasons recorded in the KDoc so nobody has
  to guess whether they were forgotten: `dynamicColor` (Material You has no cross-platform equivalent
  and would defeat the imperial palette) and edge-to-edge system bar styling (a window concern, not a
  theme concern; it stays with the Android host's `enableEdgeToEdge`).
- **`archiveTypography()` is built inline rather than remembered**, because resolving a face goes
  through `archiveFontFamily`, which is `@Composable` (see the API constraint noted above).
- `ArchiveAssistantRoot` keeps `ProvideImperialFonts` *and* adds `ArchiveAssistantTheme`, because
  call sites reading `LocalImperialFonts` directly (e.g. `PaneHeroHeader.kt:64`) would otherwise
  still fall back to serif. `NotYetMigratedPane` now reads `colorScheme.background` instead of the
  `ImperialIvory` literal so it stays legible in dark mode.

Verified with `./gradlew :shared:compileKotlinDesktop :shared:desktopTest`, incremental and
`--rerun-tasks`, no errors or warnings.

## Remaining work

1. Migrate the two remaining convertible screens (`HomePane`, `MemorialBriefingPane`) using the
   atomic-pass strategy above.
2. Re-platform `DetailPane.kt`: image decoding, a document picker, content-source materialization and
   markdown-prefill writing are four Android seams that need shared abstractions first. The screen
   cannot be converted before they exist.
3. Migrate the Canvas-heavy memorial views (10 files, ~5651 lines, incl. `MemorialFoldView.kt` at
   3661 lines). Treat as a refactor with a performance budget, not a port — the README already lists
   fold/swipe responsiveness as a known problem. `MemorialDemoOverlay.kt` belongs here too: it is a
   pure `AndroidView` wrapper around `MemorialFoldView`.
4. Finish the artwork move. **All 12 assets referenced by the migrated shared UI are bundled**, and
   the two referenced calligraphic TTFs are too (12.9 MB; `ma_shan_zheng_regular` deliberately left
   behind, unreferenced). What remains is art referenced *only* by screens that have not been
   migrated yet. Read the size finding below before copying any of it.
5. ~~Replace the placeholder hosted by `ComposeHostingViewController` with the real Compose entry
   point …~~ **Done in `be21549`** — see "The Compose root exists now" below.
6. ~~Port `ArchiveAssistantTheme` into `commonMain` so `MaterialTheme.colorScheme` stops resolving to
   the Material 3 baseline.~~ **Done in `f402b86`** (both schemes and the full type scale).
7. Wire `LiteRT-LM` through its Swift API behind the existing `LocalLlmEngine` interface.
8. Port `ModelDownloadManager` (interface exists; the 513-line OkHttp implementation does not).
9. Implement `DocumentContentExtractor` for iOS via PDFKit (currently a `NoOp` placeholder).
10. Replace the upscaled 512 px app icon with a native 1024 px asset before any App Store submission.
11. Widen the simulator smoke test beyond one iPhone portrait runtime: boot an iPad runtime and
    capture landscape too. The project builds for `TARGETED_DEVICE_FAMILY = "1,2"` and the hosting
    controller forwards orientation changes, so the two-column layout is exactly the thing the current
    coverage cannot see. Cheap to add — the script already parameterises the device and appearance.

### Artwork size: the remainder is not a free move

The 49 asset names referenced by Kotlin code total **44.9 MB** in `app/src/main/res`. For scale: the
unsigned IPA measured 5,650,070 B after the first green CI run, 14,170,223 B once the Compose root was
wired in, and 23,392,233 B at `4b0547c` once `compose-resources` was genuinely packaged — the last jump
being the fonts and the 12 already-migrated drawables. Copying the remaining artwork verbatim would add
tens of MB on top — this is a bundling decision, not a mechanical copy.

Where the weight is, and what it implies:

| Asset(s) | Size | Note |
|---|---|---|
| `home_search_tile.jpg` | 13.5 MB | 5400x3600 for a paper texture drawn scaled-to-fit behind a panel |
| 22 x `memorial_cover_*.jpg` | ~28 MB total | 750x891..750x1053, ~0.9-1.7 MB each |
| everything else referenced | ~3.4 MB | ornaments, stamps, patterns, completion art |

Two facts make this cheaper than the raw number suggests:

- **41 of the 49 assets are referenced only by the memorial reader** (`MemorialBriefingPane`,
  `MemorialFoldView` and friends). That reader is remaining work #3, an explicit refactor with a
  performance budget, so its art has no reason to land before it does.
- **`home_search_tile.jpg` is mis-sized, not genuinely large.** At 5400x3600 it is ~3.3x the linear
  need of its call site. Resampling it to 1620x1080 and re-encoding took it from 13.5 MB to 464 KB.

So the rule for the remainder: bundle an asset when a *shared* screen needs it, at a size matched to
how it is drawn; let the memorial covers arrive with the memorial-reader refactor, resampled then.

### Known gaps that are not yet a numbered item

- `:app` does **not** depend on `:shared` (`app/build.gradle.kts` declares no project dependency).
  Copied screens cannot break the legacy app, but it also means nothing in `:shared` is exercised by
  the Android app — the shared UI is verified by compilation and tests only.
- `AiEngineSettingsRepository` in `:shared` exposes only `save`. Presets are therefore not yet
  persisted on any platform even though the codec is ready; whoever wires the repositories should
  extend that interface rather than add a second storage path.
- The plan's `multiplatform-settings` row in "Library swaps" is still *planned*, not done:
  no shared code calls it yet.

### The Compose root exists now, and what it deliberately is not

Added in `be21549`. The module previously had no composition root at all — the only one was
`ArchiveAssistantApp` in the Android `:app` module — so nothing in `:shared` was ever rendered.

`shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/ArchiveAssistantRoot.kt`:

```kotlin
@Composable
fun ArchiveAssistantRoot(stateStore: ArchiveAssistantStateStore = remember { defaultStateStore() })
```

It installs `ProvideImperialFonts` around the content, reads `stateStore.state`, and dispatches:
`AppPane.SETTINGS` → `SettingsPane` (the only fully migrated screen); anything else →
`NotYetMigratedPane`, which says so in words rather than drawing an empty box that would be
indistinguishable from a layout bug.

The store needs no dependency injection to run: `ArchiveAssistantStateStore`'s constructor is
platform-free (every parameter has a default and the defaults seed the built-in sample data). Note the
Android root passes `androidContext`, `AppDataRepository`, `AiEngineSettingsRepository`,
`OkHttpModelDownloadManager` and `LocalInferenceConnection`; the two repositories are what would
persist data, and #6/#7 above are what supply the rest.

`shared/src/iosMain/kotlin/com/lyihub/archiveassistant/ui/ArchiveRootViewController.kt` is the
Swift-facing factory:

```kotlin
object IosComposeRoot {
  fun makeViewController(): UIViewController =
    createArchiveRootViewController(
      ArchiveAssistantStateStore(
        initialState = ArchiveAssistantState(selectedPane = AppPane.SETTINGS)
      )
    )
}
```

It opens on `AppPane.SETTINGS` rather than the store default `AppPane.TOPICS` on purpose: starting on
`TOPICS` lands the preview on the "not yet migrated" message and verifies nothing, and this entry point
exists to prove the resource and font pipeline renders on iOS. Two Kotlin/Native traps are worth
remembering: a top-level Kotlin function exports under a file-facade name (hence the object for
anything Swift calls), and `ComposeUIViewController` reports no reliable intrinsic content size, so the
Swift side must pin it with explicit constraints.

On the Swift side:
- `iosApp/iosApp/ComposeHostingViewController.swift` — `ArchiveComposeHostingViewController` replaced
  the placeholder. It forwards `supportedInterfaceOrientations` and `shouldAutorotate` to the child,
  because a container that inherited the portrait-only default would silently break iPad landscape.
- `iosApp/iosApp/ContentView.swift` — now `ComposeRootPreviewView`, a `NavigationStack` hosting the
  representable above. This is a **staging surface, not a product screen**, and it is intentionally
  where the Compose tree is mounted.
- `iosApp/iosApp/MainWorkspaceView.swift` — the two columns are now `UnmigratedPanePlaceholder`s and
  no longer host Compose. Reason: mounting the root in a pane would have to choose between putting a
  settings screen in the entry-detail column (a UI that lies about itself) or overturning the
  deliberately-native SwiftUI settings page. Instead a toolbar button opens the Compose root as a
  full-screen cover, which lies about nothing and can be deleted in one piece as panes land.

`onChooseModelFile = {}` is left inert on purpose: choosing a model file needs a native file picker
that does not exist yet, and a callback that pretends to work is worse than one that is visibly absent.

### Visual verification of the diagrams (how to reproduce)

The diagrams in `AI-Design/diagrams/` were verified visually. Chrome is **not** installed on this
machine, but Edge is Chromium-based and `visual-check` accepts any Chromium executable through
`ARCHIFY_CHROME`, so no install is needed:

```powershell
$env:ARCHIFY_CHROME = 'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe'
node bin/archify.mjs visual-check AI-Design/diagrams/architecture-overview.html --json
node bin/archify.mjs visual-check AI-Design/diagrams/folder-map.html --json
```

This writes the four screenshots per diagram (1440x900 and 2048x1320, light and dark), the contact
sheet and the receipt.

**Read the receipt with care — `containment.status: fail` here is a false positive.** Every viewport
reports `overflowY: true` because the page's scroll height exceeds the viewport height by 150–300 px,
but the rendered SVG is complete: at 2048x1320 the last row of nodes, the legend and the toolbar are
all inside the captured frame with blank page below them. The authoritative structural check is the
skill's own render checker, which reports `ok: true`, `composition status: pass`, `errors: 0` and
all nine checks green for both diagrams:

```powershell
node scripts/check-render-output.mjs AI-Design/diagrams/architecture-overview.html
```

The one real caveat: at a viewport height of 900 px the bottom row of `folder-map.html` is cut off
because the page scrolls, so it should be viewed at 1320 px height (or scrolled) rather than judged
from the 1440x900 screenshot.

## Tooling pitfalls encountered (avoid repeating)

- **PowerShell scripts containing non-ASCII must be saved with a UTF-8 BOM.** PowerShell reads a
  BOM-less `.ps1` using the platform code page, which truncates CJK string literals and produces
  syntax errors. Prefer ASCII-only scripts.
- **Copying source files with PowerShell round-trips through the code page and corrupts CJK.** Use
  explicit UTF-8 buffers (`[System.IO.File]::ReadAllText` / `WriteAllText` with
  `UTF8Encoding($false)`, or Node's `fs` with `'utf8'`). This corrupted `Info.plist` and several
  Kotlin files during the migration.
- **Deleting the current working directory fails with "in use".** Run the delete from a different
  `workdir`. The same applies to renaming it.
- **The session workspace is the session's `cwd`.** It is fixed at session start; there is no `dsh`
  CLI subcommand to change it. DSH writes `.dsh-edit-review*.json` into that path, so a stale
  workspace path keeps being recreated. To move a workspace, start a session from the new directory.
- **`matchParentSize` is a `BoxScope` member, not a top-level function.** Android sources carry
  `import androidx.compose.foundation.layout.matchParentSize` and compile, because inside a `Box` the
  call resolves through the scope receiver. Strip that import when porting: in `commonMain` it fails
  as `Unresolved reference 'matchParentSize'`. The tell is that no other layout modifier in the same
  import block needs a special case.
- **A compile cycle is ~5–12 minutes, so filter the log.** Piping Gradle through
  `Select-String -Pattern '^e:|BUILD |FAILURE'` turns a wall of output into the actual errors. The
  first failure in this session was reported as a single `e:` line.
- **Reserve `write` for new files and `edit` for existing ones.** Rewriting a large existing file
  to change one import invalidates the read-tracking for that path and forces a re-read before the
  next edit.
- **`Out-File -Encoding utf8` writes a BOM, and a BOM inside a git commit message is kept.** It
  showed up as a literal ``docs:`` in `git log --oneline`. Write commit messages with
  `[System.IO.File]::WriteAllText($path, $msg, (New-Object System.Text.UTF8Encoding($false)))` and
  commit with `git commit -F`.
- **A `git push` that succeeds still exits non-zero under PowerShell**, because git writes progress
  to stderr and PowerShell turns native stderr into a `NativeCommandError`. Check the
  `old..new  branch -> branch` line instead of trusting the exit code.
- **`xcrun simctl list devices` pads every device line with a trailing space**, so a `sed` pattern
  anchored on `)$` silently matches nothing and the script reports "no simulator found" while the
  listing above it looks perfect. Anchor on `[[:space:]]*$`, and prefer an explicit parse-failure
  message over a generic one — the generic one sent the search in the wrong direction. The same
  listing groups runtimes oldest-first, so "the last match" is the newest runtime, which is the
  intended choice and worth stating rather than leaving implicit.
- **Print the raw input a parser is about to consume, in full.** The trailing spaces above are visible
  in the job log, but only because the listing was dumped before the parse ran — that dump is what
  turned "the parse produced nothing" into a two-minute diagnosis instead of a guess. A `head`-ed dump
  would have hidden the newest runtime's lines and made the same bug look like a environment problem.

