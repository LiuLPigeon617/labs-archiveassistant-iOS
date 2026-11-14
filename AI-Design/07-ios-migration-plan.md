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
  ArchiveAssistantApp.swift        @main, WindowGroup -> MainWorkspaceView
  MainWorkspaceView.swift          mounts the Compose root; no native chrome left
  ComposeHostingViewController     UIViewControllerRepresentable bridge
  IosPlatformBootstrap.swift       file/resource/picker bridge install
  IosDocumentPickerHost.swift      UIDocumentPickerViewController host
  ContentView.swift                UNREACHABLE - was the preview staging surface
  SettingsView.swift               UNREACHABLE - native settings, bound to a placeholder store
  SharedStateBridge.swift          UNREACHABLE - ObservableObject over SharedStateStore

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

Status: the pipeline is green, and it has real green runs behind it. **Run 37300716982 on `fc127ec`**
is the current strongest one: both jobs — `Build unsigned IPA` and `Simulator smoke (screenshots)` —
concluded `success`, and the simulator produced three screenshots of the app rendering the migrated
homepage dashboard; see the pixel table further down. The previous milestone was **run 37214783385 on
`4b0547c`**, the first run where the Compose tree was actually on screen (the settings pane). An earlier
one was **run 37199938026 on `135d2f2`**, the first run whose Compose root compiled and archived (15 steps
plus 4 post steps, 11 minutes). Before `10a44a8` the workflow had **never** passed.

**As of 2026-11-14 the pipeline is green again, after going red for two real and unrelated reasons**
— this paragraph exists so nobody re-diagnoses them from scratch. Run 43515522851 (`f416400`) is the
green one: both jobs succeeded (~74 minutes from queue to finish), the simulator produced
all five screenshots, `crashes.txt` was empty, and the pixel check passed on both device thresholds.
The red run it replaces was 43503533031 (`ccf9f42`), which got a macOS machine and failed both jobs in
genuinely different places:

| job | failing step | cause |
|---|---|---|
| `Build unsigned IPA` | `Build unsigned .app` | Swift: `cannot convert value of type '(String?) -> KotlinUnit' to expected argument type '(String?) -> Void'` at `IosPlatformBootstrap.swift:61` |
| `Simulator smoke` | `Build and stage SharedKit.xcframework` | `java.lang.OutOfMemoryError: Java heap space` during `:shared:linkReleaseFrameworkIosArm64` |

Both were fixed in `f416400`, which is exactly the commit run 43515522851 validated. The Swift one wraps the Kotlin callback
(`onPicked: { path in _ = callback(path) }`); the memory one sets `kotlin.native.jvmArgs=-Xmx4g`,
because the plugin's default for that forked compiler JVM is `-Xmx3g` regardless of
`org.gradle.jvmargs`. Both traps are described in full under "Tooling pitfalls encountered".

A third failure mode is worth separating from these: **runs 43457410158, 43467875982 and
43474940729 were cancelled, not failed.** Their only annotation is `The job was not acquired by
Runner of type hosted even after multiple attempts` plus `Due to capacity constraints, jobs
targeting macOS arm64 runners may experience longer queue times`; each job sat queued ~15 minutes
and was cancelled without ever starting a step. Re-dispatch with
`gh workflow run ios-build.yml --ref <branch>` rather than hunting a code defect in those runs.

**The next green run will not look like any of those.** `c9b15a7` deleted the SwiftUI shell, so the
smoke test now captures three screens of one screen (iPhone light, iPhone dark, iPad light) instead of
five of two, and the pixel floor is a single `MIN_DISTINCT=300` instead of the 25 / 20 pair that was
calibrated to sit under the 86- and 81-colour native shells. Both changes are unverified: the Swift
diff is only legible to a macOS toolchain, and this commit was written while `github.com` was
unreachable from the workstation, so it had not been pushed when it was authored. Treat the first run
that includes it as the evidence, not the local build.

One operational caveat learned the hard way: **this repository's artifact retention is short** — the
screenshots from run 37300716982 were already gone (`Artifact has expired`, HTTP 410) when inspected on
2026-10-23, 18 days later. The pixel statistics survive only in that run's job log; if a capture needs to
be looked at, either download it promptly or re-dispatch the workflow (`gh workflow run ios-build.yml`)
rather than assume the artifact will still be there.

Because of that, every smoke run is now archived locally: `.github/scripts/fetch-smoke-screenshots.sh`
pulls one run's artifact into `screenshot/<runId>-<shortSha>/` under the repository root and keeps only
the **two most recent** archives, pruning the rest. It stores the PNGs plus `summary.md`,
`screenshot-checks.txt`, `crashes.txt` and the app/console logs, and drops the `xcodebuild` logs, which
are large and can be re-read from the run page. The images are deliberately **not** committed — only
`screenshot/README.md` documents the convention, and `.gitignore` excludes the rest of the directory.
Failed runs are downloadable too, which matters because a failed run's screenshots are usually the ones
worth looking at. Pruning sorts numerically by run id rather than by file timestamp, since run ids increase
monotonically and a copy or checkout may not preserve mtimes.

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

Three screenshots, because they answer different questions. They used to include two native shell
captures (`01-native-shell-light.png`, `04-ipad-native-shell-light.png`); those are gone with the shell.

| Screenshot | Launch | Question it answers |
|---|---|---|
| `01-compose-home-light.png` (iPhone) | no argument | does the shared Compose dashboard render, and do the bundled fonts show |
| `02-compose-home-dark.png` (iPhone) | no argument, dark appearance | did `isSystemInDarkTheme()` resolve to the dark scheme at first composition |
| `03-ipad-compose-home-light.png` (iPad) | no argument | does the dashboard reflow correctly at 2064pt, and does the font/resource pack still load there |

Names have moved twice and each move is recorded rather than retro-fixed: `02/03-compose-settings-*.png`
while settings was the only migrated pane (run 37214783385 keeps those names), then
`02/03-compose-home-*.png` once the entry point moved to the dashboard, and now `01/02-…` on the iPhone
plus `03-…` on the iPad because the two native-shell captures (`01-native-shell-light.png`,
`04-ipad-native-shell-light.png`) were deleted with the shell. Artifacts already published under the
older names are left alone — renaming a capture that exists would make the old runs unfindable.

All three upload in the `ios-simulator-smoke` artifact. That artifact is the point: it answers the
font question by *showing* it, which is the only way that question can be answered.

**Two deliberate build choices.** The simulator build runs `-sdk iphonesimulator -configuration Debug`
with `ARCHS=arm64` and **without** the signing overrides the archive build needs — the simulator SDK
expects an ad-hoc signature and `simctl install` accepts the default product, so
`CODE_SIGNING_ALLOWED=NO` would only risk producing a bundle the simulator refuses. And the dark
screenshot relaunches under a switched appearance rather than toggling it under a running app, because
that is what exercises the value `isSystemInDarkTheme()` resolves at *first* composition.

**The `--compose-preview` launch argument is gone.** It existed because `simctl launch` can pass
arguments but cannot tap a toolbar button, and driving the UI instead would have meant adding a UI
automation target to the Xcode project. With `MainWorkspaceView` mounting the Compose root directly,
the launch starts in Compose and the argument has no reader, so it was removed from the script and the
docs. The manual equivalent, useful locally on a Mac, is now argument-free:

```bash
xcrun simctl launch booted com.lyihub.archiveassistant
```

**What the pixel check does and does not prove.** `.github/scripts/check-screenshot.swift` decodes each
PNG, quantises colours to 5 bits per channel and fails if fewer than a per-device minimum appears. The
threshold is calibrated against real captures rather than guessed — measured with the same sampling and
quantisation the script uses, on run 37203635522 (where every Compose launch crashed) and run 37214783385
(the first run where Compose actually rendered):

| capture | distinct colours | source |
|---|---|---|
| genuinely blank screen | 1–3 | — |
| Compose settings pane, light (the pane the preview opened on then) | **167** | 37214783385 |
| Compose settings pane, dark (same) | **154** | 37214783385 |
| Compose home dashboard, light | **1153** | 43515522851 |
| Compose home dashboard, dark | **1154** | 43515522851 |
| iPad Compose home, light | **1177** | 39979857765, 43515522851 |
| springboard fallback after a crash | **2663 / 2822** | 37203635522 |
| *(retired)* iPhone native SwiftUI shell | 86 | 39979857765, 43515522851 |
| *(retired)* iPad native SwiftUI shell | 81 | 39979857765, 43515522851 |

**The threshold history, because it explains the current number.** 50 first, which put legitimate
content only 36 shades from failing. Then 25, calibrated against the sparse native shell (86) that the
iPhone pass led with. Then, when the iPad pass arrived, two values — 25 for iPhone, 20 for iPad —
because the iPad shell measured 81, five colours *below* the iPhone's, so no single value could sit
under both floors at the same relative distance.

**All of that is now gone, and the current value is 300.** Every capture is the same screen, so the
sparse floor those numbers were guarding disappeared with the shell; keeping 25 would have meant
keeping a threshold that a dashboard missing 97% of its colours still passes. 300 is 1153 / 300, i.e.
**3.84× headroom** — slightly more than the 86 / 25 = 3.44 the old iPhone value carried — and ~100×
a blank screen. Low enough that a partially-drawn dashboard is not failed on grounds of taste, high
enough that an empty or unmounted screen cannot pass. `MIN_DISTINCT` still overrides it for a one-off run.

Note the last two rows: a crashed app's screenshot is *the most colourful image of the set*, so this
check cannot detect the failure that matters most. It catches exactly one thing — a blank, flat or unmounted screen — and will
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

**The dashboard is on screen too (run 37300716982, commit `fc127ec`).** After `HomePane` was migrated and
the preview entry point moved from `AppPane.SETTINGS` to `AppPane.TOPICS`, the same job reports pixel
statistics that make the difference legible in numbers:

| capture | distinct colours | mean luminance |
|---|---|---|
| `01-native-shell-light.png` | 86 | 237.6 |
| `02-compose-home-light.png` | 1153 | 202.8 |
| `03-compose-home-dark.png` | 1154 | 111.3 |

Before the migration the two Compose captures measured 167 and 154. The dashboard's tile backgrounds,
cut-corner cards and ornament overlays push that past a thousand, so the check is no longer merely
asking "did anything paint" — a placeholder or a mostly-empty pane could not reach that count. The dark
capture's mean luminance halving is also the only run so far where `isSystemInDarkTheme()` was exercised
on a screen with more than one surface on it.

The honest caveat: richer pixels are not proof the layout is right. Nothing here verifies that the
mosaic's columns line up, that the ornaments are the intended artwork, or that a tap does anything.

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

**iPad is covered now; landscape is not, and will not be by this script.** The iPad pass boots an
`iPad Pro` runtime and captures its native shell and Compose tree (run 39979857765: 2064x2752, 81 and
1177 distinct colours), so the `NavigationSplitView` layout — the reason `TARGETED_DEVICE_FAMILY` is
`"1,2"` — is finally on screen in CI.

Landscape is *not* covered and the three obvious routes are dead ends rather than untried options:
`simctl` has no rotation command, the app's own orientation mask is ignored on iPadOS' resizable-window
model, and `devicectl` has no orientation subcommand. Run 40064146914 proved the second one the
expensive way — with a landscape-only mask the iPad still produced a 2064x2752 portrait image. Rotating
through `XCUIDevice.shared` requires a UI-test target, so it waits on a separate branch. The commit
that tried landscape lived on this branch and was reverted (`6993db2`).

A screenshot of a pane is also not a test that the pane *works*: the capture is the first frame, so
tapping through the dashboard's tiles is still out of scope.

One specific gap inside the current coverage: **the XML vector drawables have never been confirmed to
render on iOS.** Five of the `HomePane` ornaments and the six `tsieina_department_pattern_*` folder
patterns are Android-format `<vector>` XML. Compose Multiplatform documents support for that format
(`painterResource` returns a `VectorPainter`), and all ten files pass the "no external Android resource
references" restriction, but the only proof so far is that the compile and the pixel check pass.
`archivePainterOrPlaceholder` returns a 1×1 transparent painter for an asset it cannot resolve, so a
parse failure in `:shared` would draw nothing and still sail through `check-screenshot.swift`. Confirming
this needs a human looking at the capture, not a green job.

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

`./gradlew :shared:compileKotlinDesktop :shared:desktopTest` passes. 25 files now live in
`shared/src/commonMain/.../ui/`:

- `theme/`: Color, ImperialFonts, ImperialPalette, Theme
- `components/`: ActionButton, ArchiveChip, ArchiveDialog, ArchiveNoticeBanner, HeaderBackButton,
  PaneContainer, PaneHeader, XuanPaperBackground
- `layout/`: LayoutMode
- `screens/`: ArchiveVisuals, HomePane, MemorialBriefingPane, MemorialCoverSequence,
  MemorialStackGeometry, PaneHeroHeader, SettingsPane, TagVisuals, TopicManagementDialogs
- root: ArchiveAssistantRoot (the composition root), ArchivePainter, ArchiveFont (the two seam objects)

The platform seams that the *next* screen needs are also in `commonMain` now, outside `ui/`:
`platform/ContentImport.kt` (`ImagePixelSize`, `ImageBytesDecoder`, `ContentPicker`) and
`data/ContentMaterialization.kt` (`ContentImporter`, `writeMarkdownPrefill`, `ImportNaming.kt`). See
*The four `DetailPane` seams are now shared abstractions*.

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

The same class of failure recurred during the `HomePane` port — this time a **static call**, not an
import:

```
e: HomePane.kt:668:47 Unresolved reference 'Math'.
e: HomePane.kt:668:55 Unresolved reference 'times' for operator '*'.
> Task :shared:compileKotlinIosArm64 FAILED
```

The line was `kotlin.math.sin(progress.value * Math.PI * 2.0)`. `Math.PI` is `java.lang.Math`,
which exists on JVM and Android and is **absent from Kotlin/Native**; `kotlin.math.PI` is the
portable spelling. Note the shape of this miss: the porting pass had already replaced the *function*
`sin` with its `kotlin.math` form but left the *constant* `Math.PI` beside it, so a grep for
`Math\.` hit the line and it was waved through as "already handled". Fixed in `a0d9f4c`.

Practical rule: **when a `commonMain` file is ported from Android, strip the `androidx.compose.ui.res`
import block even if the build is green**, and treat the macOS CI run as the only authoritative
iOS compile. Two cheap pre-flight checks, both required:

- `androidx\.compose\.ui\.res|LocalContext|^import android\.` — catches the import form. The only
  legitimate hits live in `androidMain` (`platform/Platform.kt`, `data/Support.kt`), which iOS never
  compiles.
- `Math\.|String\.format|\.format\(|Locale|System\.(currentTimeMillis|nanoTime)|java\.(lang|util|io)\.`
  — catches the *call* form, which the import check misses entirely. In `commonMain` the legitimate
  hits are only `kotlin.math.*` (a different token: `kotlin.math.PI`, never bare `Math.PI`) and
  `Clock.System.now()`. Anything else is a defect, not a style nit.

### Remaining portable screens, and the strategy required

Nothing is left in `:app` that is convertible this way. `HomePane.kt` (1493 lines) and
`MemorialBriefingPane.kt` (805 lines) both went through it and now live in
`shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/screens/`; see their sections below.

Both were originally attempted and reverted, because they thread Android `R.drawable` **`Int` resource
ids** through many private helper composables. Converting them incrementally leaves the module
uncompilable and produces half-converted files, which is why the pass has to be atomic.

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

**All four abstractions now exist** — see *The four `DetailPane` seams are now shared
abstractions*, further down.

**Update: the display half is now migrated.** `shared/.../ui/screens/DetailPane.kt` carries the first
604 lines of the Android file — the memorial wall (two-column masonry), the tag filter bar, the empty
shelf and the decorative corner. The remaining three composables stay in `:app` for now:
`AddItemDialog` and `ClipboardDialog` (they need the picker *and* an image preview) and
`DeleteItemConfirmDialog` (pure copy, dragged along with them to keep the trio together).

Net: **two** screens *were* convertible, not four — a scope reduction of ~1500 lines against what this
document previously implied. Both have since been migrated: `HomePane` and `MemorialBriefingPane`.
`DetailPane`'s wall now renders from `commonMain` on every target as well. Everything left there is
the three dialogs; everything left overall is the memorial reader's Canvas refactor (remaining work
#3).

### The four `DetailPane` seams are now shared abstractions

`DetailPane.kt` cannot be converted by renaming resources, because four of its dependencies are image
and file platform APIs. Those four are now behind `commonMain` interfaces with one implementation per
platform — `shared/.../platform/ContentImport.kt` and `shared/.../data/ContentMaterialization.kt`:

| `DetailPane.kt` dependency | shared abstraction | platform sides |
|---|---|---|
| `BitmapFactory` + `resources.openRawResource` (measure, then decode) | `ImageBytesDecoder.readSize` / `.decode` → `ImageBitmap`, from `platformImageDecoder()` | Android `BitmapFactory.decodeByteArray` (`inJustDecodeBounds` for the size); desktop and iOS `decodeToImageBitmap` |
| `rememberLauncherForActivityResult(GetContent())` + `Uri` | `ContentPicker.pick(allowedExtensions): ContentSource?`, from `platformContentPicker()` | Android injected via `AndroidContentPickerHost`; desktop `JFileChooser`; iOS `UIDocumentPickerViewController` via `IosNativeBridge.pickFile` |
| `copyUriToFile` + `uniqueImportFile` | `ContentImporter.materialize(source, directory): String?` | one common `FileStoreContentImporter` over `PlatformFileStore` — the platform half is already behind `ContentSource.openRead` |
| `writeMarkdownPrefillFile(context, title, content)` | `writeMarkdownPrefill(fileStore, itemsDir, title, content): String?` | common, over `PlatformFileStore` |

Six things were judgements, not transcription:

- **`ImageBitmap` rather than `ByteArray` as the decode result.** `Bitmap` is not a multiplatform type,
  and handing bytes back would make every caller re-implement decoding. `ImageBitmap` is what all four
  targets paint.
- **`ContentSource` rather than a path or a `Uri` at the picker boundary.** `Uri` does not exist on
  Kotlin/Native at all, and a bare path carries no display name. The existing `resolveContentSource(key)`
  seam already models *something I can open and read*, so the picker returns that same type; `null` means
  cancelled, or no picker installed yet.
- **The Android picker is injected, not implemented.** `rememberLauncherForActivityResult` only works
  inside a composable with an activity host, and `:shared` deliberately has no `androidx.activity`
  dependency — adding one would put an Android-only artifact on the iOS compile classpath. So
  `AndroidContentPickerHost.install(picker)` mirrors the existing `AndroidPlatformContext` shape; until a
  host installs one, `CancellingContentPicker` answers `null`, which reads as a cancelled pick. This is
  the one seam whose Android side is still a host obligation.
- **`writeMarkdownPrefill` is a new function, not a change to `writeMarkdownFile`.** The older
  `data/Support.kt:8` `expect fun writeMarkdownFile` overwrites a same-named note and is not `suspend`.
  The `DetailPane` path needs `markdownFileName`'s naming *and* uniqueness. Two behaviours under one name
  is a trap, so the old function keeps its semantics and only new call sites use the new one.
- **desktop and iOS decode with `decodeToImageBitmap`, never skiko directly.**
  `androidx.compose.ui.graphics.ImageBitmapKt.decodeToImageBitmap(byte[])` is present in
  `ui-graphics-desktop-1.8.0.jar` (checked with `javap`). Importing `org.jetbrains.skia.Image` instead
  would put skiko on the iOS compile classpath, where `:shared` does not declare it — a failure only a
  macOS CI run would reveal. For the record, `toComposeImageBitmap` and `asComposeImageBitmap` appear
  nowhere in the compose artifacts: grep count zero.
- **Six duplicated lines beat a `skikoMain` source set.** Sharing the two decoders would need a new
  source-set tier verified only by a ~40-minute macOS CI run, to delete six lines. If a third decoder
  caller ever appears, promote the source set then.

One deliberate imperfection: `readSize` on desktop and iOS decodes the whole image, because skiko has no
bounds-only decoder. `DetailPane` measures one image per open, so this is recorded rather than hidden.

`ContentImport.kt` also gained a real iOS picker to go with the seam —
`iosApp/iosApp/IosDocumentPickerHost.swift` presents a `UIDocumentPickerViewController`, opens the
security scope, and hands back a plain path (copying into the temporary directory when the provider
ignores `asCopy`), because `:shared` reads files by path.

Verified locally with `./gradlew --offline :shared:compileKotlinDesktop :shared:desktopTest` → BUILD
SUCCESSFUL, 44 tests / 0 failures, 11 of them new in `ContentMaterializationTest` (naming, uniqueness,
the unreadable-source path, the empty-directory path). **Not** verified: the Android implementation (no
Android SDK on this machine, and `:app` does not depend on `:shared`), the iOS implementation and the
Swift picker host (needs macOS and a human tapping a button), and the pickers in general — a fake can
only test a fake.

### `HomePane` migrated (compiles and renders green)

`app/src/main/java/com/lyihub/archiveassistant/ui/screens/HomePane.kt` (1493 lines) is now
`shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/screens/HomePane.kt` (~1010 lines). The
public composable signature is **unchanged** — same parameter names, same order, same defaults — so the
Android host's call site at `ArchiveAssistantApp.kt:589-605` would compile against either copy.

The atomic pass from the strategy above, applied in full. Six seams, each mechanical:

| Android | shared |
|---|---|
| `@DrawableRes ornamentRes: Int` + `painterResource(id = R.drawable.X)` | `ornamentAsset: String` + `archivePainterOrPlaceholder("X")` |
| `Icons.Default.Settings` / `Icons.Default.Close` | locally drawn `SettingsGlyph` / `ClearGlyph` |
| `ImperialTitleFont` / `ImperialDisplayFont` | `LocalImperialFonts.current.title` / `.display` |
| `System.currentTimeMillis()` | `Clock.System.now().toEpochMilliseconds()` |
| `TOTAL_PENDING_MEMORIALS` (in `MemorialDemoModels.kt` in `:app`) | same-named `internal const` in `ArchiveVisuals.kt` |
| `toChineseCount` from `com.lyihub.archiveassistant.util` | already existed in `:shared` at `util/ChineseNumerals.kt:3` |

Four things are worth recording because they were judgement calls, not transcription:

- **`overscrollEffect = null` was kept.** It is an Android-era argument to `verticalScroll`; the
  overload taking `OverscrollEffect` is in the common API from Compose 1.7, verified by `javap` against
  `foundation-desktop-1.8.0.jar` (both overloads present). It is the first suspect if the iOS compile
  ever rejects the file.
- **The fonts were not switched to `MaterialTheme.typography.*`** even though `archiveTypography()` now
  supplies the same faces. Reason: the port's goal was line-for-line equivalence with Android, and the
  explicit `.copy(fontFamily = …)` is redundant but harmless. Changing it would silently make the two
  copies diverge on a detail nobody asked to change.
- **The two icons are drawn locally rather than injected.** `:shared` has no icon dependency (the Gradle
  cache holds only Android's `material-icons-core`), and an injected slot for two call sites pushes that
  dependency onto every host. They carry `semantics { contentDescription }` so the affordances stay
  reachable — `HeaderBackButton` set that precedent.
- **The tile art was re-encoded at q85 but not downscaled**, unlike `home_search_tile`; the reasoning is
  in the artwork section. The five tiles are drawn full-bleed, so resolution is load-bearing here.

Not ported, and not needed by it: `HomePane` reads no `Context`, no `Uri`, no `BitmapFactory`. It is
genuinely a resource-id conversion, which is why it could be done atomically.

The root component now dispatches `AppPane.TOPICS` to it, and `TopicManagementDialogs` is composed
outside that dispatch. `SettingsPane` was the only entry until now.

Status of the port: **compiles and renders, verified by CI**. Run 37299369306 (commit `777c744`) failed
on an iOS-only compile error — `HomePane.kt:668` used `Math.PI`, and Kotlin/Native has no `java.lang.Math`
facade, so `efab267` switched it to `kotlin.math.PI`. That failure is the whole reason the pre-flight
check in the section above exists: the desktop JVM build accepted `Math.PI` happily. Run 37300716982 on
`fc127ec` is the first green run with the dashboard on screen.

Also worth stating plainly: **the dashboard's "新建 / 改名 / 删除" actions do nothing on the shared side,
by design of the store, not by accident of this port.** `ArchiveAssistantStateStore.openCreateTopicDialog()`
and `openRenameTopicDialog()` set `topicNameDialogMode = null` and `topicValidationMessage =
TOPIC_CRUD_DISABLED_MESSAGE`, so `TopicManagementDialogs` never opens. That matches the README's "六部分类
当前固定", but it means a reviewer poking at the migrated homepage should not read the dead buttons as a
migration defect.

### `MemorialBriefingPane` migrated (compiles green, not yet seen on screen)

`app/src/main/java/com/lyihub/archiveassistant/ui/screens/MemorialBriefingPane.kt` (805 lines) is now
`shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/screens/MemorialBriefingPane.kt`
(~841 lines). The public composable signature is **unchanged** — same parameter names, same order,
same defaults — so the Android host's call site at `ArchiveAssistantApp.kt:608-625` would compile
against either copy.

The atomic pass, applied in full, with the two judgement calls that were *not* mechanical:

| Android | shared |
|---|---|
| `@param:DrawableRes departmentImageRes: Int` + `painterResource(id = R.drawable.X)` | `departmentImageAsset: String` + `archivePainterOrPlaceholder("X")` |
| `MemorialCoverResources: List<Int>` (23 entries in `ArchiveVisuals.kt`) | `MemorialCoverAssets: List<String>` (6 entries) |
| `ImperialStampTitleFont` / `ImperialDisplayFont` | `LocalImperialFonts.current.title` / `.display` |
| `Icons.AutoMirrored.Filled.ArrowBack` (via `PaneHeroHeader`'s default) | locally drawn `BackArrowGlyph` |
| `Math.toRadians(degrees.toDouble())` | `degrees * (PI / 180.0)` |
| `smoothStep` from `MemorialTextDrawing.kt:84` | a private copy in the pane |

Three things are worth recording because they were decisions, not transcription:

- **The cover pool drops from 23 to 6.** `MemorialCoverAssets` in
  `shared/.../ui/screens/ArchiveVisuals.kt` holds `memorial_cover_pattern` plus `_02` through `_06`;
  covers `_07`..`_23` stay in `:app` with the memorial reader. This is deliberate and follows the
  artwork rule below: the 22 `memorial_cover_*.jpg` are ~28 MB and 41 of the 49 outstanding assets are
  referenced only by the reader that has not been migrated. The wheel still looks populated because
  `MemorialCoverSequence.wheelResources` rotates a duplicate guard over whatever pool it is given — it
  just has fewer distinct faces than Android. **When the reader lands, this list grows back.**
- **`MemorialCoverSequence` was genericised, not forked.** Its `wheelResources` and four validation
  helpers took `List<Int>`; they now take `List<T>`, so one implementation serves the Android `Int`
  pool and the shared `String` pool. A second copy keyed on `String` would have drifted.
- **The back arrow is drawn locally.** `:shared` has no Material Icons dependency. `BackArrowGlyph`
  reuses `HeaderBackButton`'s geometry (a shaft plus a chevron head, `strokeWidth = height * 0.11`)
  and is passed into `PaneHeroHeader`'s `backIcon` slot, which is how the header was designed to be
  fed. The slot has **no fallback**: when it is null the `IconButton` renders an empty box, still
  occupying its touch target. That silence is exactly how an icon seam gets lost, so it is called out
  here.

Status: **compiles and tests pass locally** (`./gradlew :shared:compileKotlinDesktop :shared:desktopTest`,
33 tests / 0 failures), and the root dispatches `AppPane.MEMORIAL` to it. It has **not** been seen on
screen — the next CI run that reaches the smoke test is the first evidence, and even then the smoke
test only captures the *entry* pane, so the briefing pane is not in those screenshots. Reaching it
requires tapping through to it on a device.

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

**Every asset the migrated shared UI actually references is now bundled** — 28 files, 3,376,501 B. The
pack grew from 12 files because the HomePane port brought its own art: five tile JPEGs
(`home_zhongshu_tile`, `home_menxia_tile`, `home_memorial_tile`, `home_clipboard_tile`,
`home_search_new_tile`) re-encoded at their original pixel size, three ornament PNGs, two ornament vector
XMLs, and six `tsieina_department_pattern_*` vector XMLs for the folder cards.

`home_search_tile` was resampled from 5400x3600 (13.5 MB) to 1620x1080 (464 KB, ~3.4%) — it is drawn
`ContentScale.Crop` as a paper texture under a panel, so the original resolution was ~3.3x beyond what any
iPad renders, and 1620 px keeps a 30% linear sample of the source. If it ever looks soft on a large
display, 2160x1440 costs 864 KB.

The five tile JPEGs were re-encoded at q85 **without** downscaling (208,925 / 339,795 / 264,063 /
406,394 / 170,820 B), which is a deliberately conservative choice: these are drawn as full-bleed
backgrounds behind the dashboard tiles, so a resolution cut would be visible, while the JPEGs had a lot
of lossless-recompression headroom. Unlike the 5400x3600 source above, none of them was mis-sized.

The vector XMLs are a risk worth naming: Compose Multiplatform documents Android XML vector drawables as
supported (they become `VectorPainter`) with the single restriction that external references to Android
resources are not allowed. All ten XMLs here have a `<vector>` root and zero `@color` / `@drawable` /
`?attr` / `@android:` hits, so they satisfy that restriction — but iOS rendering of them is verified only
by the smoke screenshot, which shows the dashboard; a silently blank ornament would pass the pixel check.

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
Icon affordances are injected slots (`backIcon`, `settingsIcon`) or drawn locally. `HeaderBackButton`
was the first local glyph; the HomePane port added `SettingsGlyph` and `ClearGlyph` for the same reason —
injecting an `ImageVector` for two call sites would push the dependency onto the host. (`CloseSearchGlyph`
was named in an earlier draft of this document but never existed in code.)

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

1. ~~Migrate `MemorialBriefingPane` using the atomic-pass strategy above.~~ **Done** — see the
   MemorialBriefingPane section. The atomic-pass list is now empty.
2. ~~Re-platform `DetailPane.kt`.~~ **The display half is done**: `shared/.../ui/screens/DetailPane.kt`
   (647 lines) renders the wall, the filter bar, the empty shelf and the decorative corner on every
   target, and `ArchiveAssistantRoot` dispatches to it on `AppPane.DETAIL`. What is still in `:app`
   is the three dialogs — `AddItemDialog`, `ClipboardDialog`, `DeleteItemConfirmDialog`. Their
   platform seams (`platformImageDecoder`, `platformContentPicker`, `contentImporter`,
   `writeMarkdownPrefill`) already exist with Android, desktop and iOS implementations; what remains
   is rewriting the dialogs themselves against them. Two host obligations stay open: the Android picker
   must be installed through `AndroidContentPickerHost`, and the iOS picker needs a human to tap it on
   a simulator before it can be called working.
   **One deliberate behaviour change in the port:** the card's image aspect ratio is measured from the
   painter's `intrinsicSize` rather than from `BitmapFactory.Options(inJustDecodeBounds = true)`.
   Both read the same already-decoded bitmap for a bundled asset, so the number is the same; the
   difference is what happens when an asset is *not* bundled — the placeholder is 1×1 and is detected
   as such, so the card falls back to the estimate instead of cropping a square.
3. Migrate the Canvas-heavy memorial views (10 files, ~5651 lines, incl. `MemorialFoldView.kt` at
   3661 lines). Treat as a refactor with a performance budget, not a port — the README already lists
   fold/swipe responsiveness as a known problem. `MemorialDemoOverlay.kt` belongs here too: it is a
   pure `AndroidView` wrapper around `MemorialFoldView`.
4. Finish the artwork move. **All 28 assets referenced by the migrated shared UI are bundled**, and
   the two referenced calligraphic TTFs are too (12.9 MB; `ma_shan_zheng_regular` deliberately left
   behind, unreferenced). What remains is art referenced *only* by screens that have not been
   migrated yet. Read the size finding below before copying any of it.
5. ~~Replace the placeholder hosted by `ComposeHostingViewController` with the real Compose entry
   point …~~ **Done in `be21549`**, and the SwiftUI shell around it was deleted in `c9b15a7` — see
   "The Compose root exists now" below.
6. ~~Port `ArchiveAssistantTheme` into `commonMain` so `MaterialTheme.colorScheme` stops resolving to
   the Material 3 baseline.~~ **Done in `f402b86`** (both schemes and the full type scale).
7. Wire `LiteRT-LM` through its Swift API behind the existing `LocalLlmEngine` interface.
8. Port `ModelDownloadManager` (interface exists; the 513-line OkHttp implementation does not).
9. Implement `DocumentContentExtractor` for iOS via PDFKit (currently a `NoOp` placeholder).
10. Replace the upscaled 512 px app icon with a native 1024 px asset before any App Store submission.
11. ~~Widen the simulator smoke test beyond one iPhone portrait runtime.~~ **iPad done in `39979857765`,
    thresholds calibrated from that run's captures, and re-based when the SwiftUI shell was deleted:**
    the script boots an `iPad Pro` runtime and captures `03-ipad-compose-home-light.png` alongside the
    two iPhone dashboard shots. The threshold is back to a single value, `MIN_DISTINCT=300`, because
    every capture is now the same Compose screen — the per-device floors (iPhone 25 / iPad 20) only
    existed to sit under the two different native-shell colour counts (86 and 81), and those captures
    no longer exist. 300 is 3.84× headroom under the measured 1153–1177 and ~100× a blank screen.
    **Landscape stays out**, for the reasons in *Tooling pitfalls*: no `simctl` rotation, the app mask
    ignored by iPadOS window resizing, no `devicectl` orientation subcommand. It needs a UI-test target
    on a separate branch.

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
- ~~`AiEngineSettingsRepository` in `:shared` exposes only `save`.~~ **Closed** — the interface now
  has `load()` as well, and the startup path reads the stored AI settings *before* restoring the
  local-model state, because that state is keyed by `aiSettings.localModelId`: restoring first would
  make every launch ask the user to download the model again.
- The plan's `multiplatform-settings` row in "Library swaps" is still *planned*, not done:
  no shared code calls it yet.

### Data persistence landed, and the seam it needed

Before this change the app had **no persistence at all**: `ArchiveAssistantStateStore` declares
`appDataRepository` and `aiSettingsRepository` with `null` defaults, and `loadPersistedStateAsync()`
(`:120`) and `saveData()` (`:157`) both begin with `?: return`, so every launch seeded the sample data
again and every edit was dropped when the app closed. The Android `:app` module has the real thing;
`:shared` had only the interfaces.

What landed:

- `shared/src/commonMain/.../data/AppDataCodec.kt` — the `org.json` codec from
  `app/src/main/java/com/lyihub/archiveassistant/data/AppDataPreferences.kt` rewritten on
  `kotlinx.serialization.json`. It is hand-rolled rather than `@Serializable` on the domain classes for
  two reasons: the wire format must stay byte-compatible with what Android already wrote (the key names
  are asserted in tests), and `Topic`/`KnowledgeItem` are UI-facing model types that should not grow
  serialization annotations to serve one storage format.
- `shared/src/commonMain/.../data/AppDataRepositories.kt` — `FileAppDataRepository` (one file per
  payload: `app_topics_json.json`, `app_items_json.json`) and `FileAiEngineSettingsRepository`
  (`ai_engine_settings.json`).
- `PlatformFileStore.stateDir` — a new platform seam, plus implementations on all three targets
  (iOS through `IosNativeBridge.stateDir`, desktop under the temp root, Android under `filesDir`) and
  the matching directory creation in `iosApp/iosApp/IosPlatformBootstrap.swift`.
- `AiEngineSettingsRepository.load()` — the interface only had `save`.

Three decisions worth keeping:

1. **`stateDir` is not `itemsDir`.** Item files are re-exportable artefacts; the archive snapshot is
   the only copy of what the user typed, so it gets its own directory and a possibly different
   lifetime.
2. **A blank `stateDir` disables persistence rather than falling back to a relative path.** iOS reports
   blank before Swift installs the bridge handler; writing `./app_topics_json.json` into whatever the
   process cwd happens to be would be worse than not persisting.
3. **AI settings load *before* the local-model state is restored.** `restoreLocalModelState()` keys off
   `aiSettings.localModelId`; restoring first would leave the app thinking no model is installed and
   re-prompting for a download on every launch.

Decoding is deliberately strict about required keys and lenient about optional ones: a missing
`iconColor` is corruption (silently defaulting it would overwrite the user's archive with defaults on
the next save), while a missing or empty `sourceUrl` is `null` (that is how the Android encoder writes
absent optionals), and an unknown `documentFormat` becomes `null` rather than corruption so a future
Android format does not brick the iOS app.

Verified with `:shared:compileKotlinDesktop :shared:desktopTest`: 68 tests, 0 failures, including 15 new
codec tests (`AppDataCodecTest`) and 9 new repository tests (`FileAppDataRepositoryTest`). **Not yet verified on a device** — that the file lands in the
app-support directory and survives a relaunch is exactly what the iOS target cannot be checked for
locally; it needs a CI run and a person to quit and reopen the app.
### The Compose root exists now, and what it deliberately is not

Added in `be21549`. The module previously had no composition root at all — the only one was
`ArchiveAssistantApp` in the Android `:app` module — so nothing in `:shared` was ever rendered.

`shared/src/commonMain/kotlin/com/lyihub/archiveassistant/ui/ArchiveAssistantRoot.kt`:

```kotlin
@Composable
fun ArchiveAssistantRoot(stateStore: ArchiveAssistantStateStore = remember { defaultStateStore() })
```

It installs `ProvideImperialFonts` and `ArchiveAssistantTheme` around the content, reads
`stateStore.state`, and dispatches: `AppPane.TOPICS` → `HomePane` (the dashboard), `AppPane.MEMORIAL` →
`MemorialBriefingPane`, `AppPane.SETTINGS` → `SettingsPane`; anything else → `NotYetMigratedPane`, which
says so in words rather than drawing an empty box that would be indistinguishable from a layout bug.
`TopicManagementDialogs` is composed outside the dispatch, matching the Android host — HomePane only
*requests* a rename or delete through callbacks.

The `MEMORIAL` branch mirrors the Android host's **phone** layout: it passes `showBackButton = true` and
`onBack = stateStore::closePanes`, because on a phone the briefing occupies the whole screen. The
Android wide layout passes `showBackButton = false` instead; this root has no wide layout yet, so the
phone behaviour is the one carried over. `onOpenMemorialDemo = {}` is inert on purpose for the same
reason as `onChooseModelFile`: tapping the briefing opens `MemorialDemoOverlay`, which is a pure
`AndroidView` wrapper around `MemorialFoldView` (remaining work #3).

`HomePane` was added to the dispatch after being migrated; `SettingsPane` was the only entry for a while,
which is why older sections of this document read as if settings were the whole UI.

The store needs no dependency injection to run: `ArchiveAssistantStateStore`'s constructor is
platform-free (every parameter has a default and the defaults seed the built-in sample data). Note the
Android root passes `androidContext`, `AppDataRepository`, `AiEngineSettingsRepository`,
`OkHttpModelDownloadManager` and `LocalInferenceConnection`.

**The two repository slots are now filled on every target** (`defaultStateStore()` in
`ArchiveAssistantRoot.kt` is `internal` exactly so `IosComposeRoot` can call it):
`FileAppDataRepository` and `FileAiEngineSettingsRepository` both write JSON under the new
`PlatformFileStore.stateDir` seam. That seam is deliberately separate from `itemsDir` — item documents
are re-exportable artefacts, whereas the archive snapshot is the only copy of what the user typed. A
blank `stateDir` (Swift has not installed the bridge yet) disables persistence instead of writing to a
relative path, so the app degrades to its old amnesiac behaviour rather than scattering files. See
"Data persistence landed" below.

`shared/src/iosMain/kotlin/com/lyihub/archiveassistant/ui/ArchiveRootViewController.kt` is the
Swift-facing factory:

```kotlin
object IosComposeRoot {
  fun makeViewController(): UIViewController =
    createArchiveRootViewController(defaultStateStore(initialPane = AppPane.TOPICS))
}
```

It opens on `AppPane.TOPICS` — the dashboard — which is also the store's own default; the value is spelled
out so the entry point does not silently follow a future change to that default. It used to open on
`SETTINGS`, which was correct only while settings was the sole migrated pane. Two Kotlin/Native traps are
worth remembering: a top-level Kotlin function exports under a file-facade name (hence the object for
anything Swift calls), and `ComposeUIViewController` reports no reliable intrinsic content size, so the
Swift side must pin it with explicit constraints.

On the Swift side there are only three live files left:
- `iosApp/iosApp/ComposeHostingViewController.swift` — `ArchiveComposeHostingViewController` replaced
  the placeholder. It forwards `supportedInterfaceOrientations` and `shouldAutorotate` to the child,
  because a container that inherited the portrait-only default would silently break iPad landscape.
- `iosApp/iosApp/MainWorkspaceView.swift` — now three lines of body: the representable above with
  `.ignoresSafeArea()`. It is the whole product screen.
- `iosApp/iosApp/ArchiveAssistantApp.swift` — `@main`. It keeps the `@UIApplicationDelegateAdaptor`
  (without it SwiftUI never calls `AppDelegate`, so the Kotlin bridge never gets its implementations)
  and no longer holds a `@StateObject`.

**Why the shell was deleted rather than shrunk.** The old `MainWorkspaceView` was a
`NavigationSplitView` with two `UnmigratedPanePlaceholder` columns, a toolbar preview button, and a
toolbar gear opening a native `SettingsView`. That gear was the problem: `SettingsView` read and wrote
`SharedStateBridge`, which wraps `SharedStateStore` — a placeholder whose `updateAiSettings()` assigned
a field and whose `startModel()`/`stopModel()` had empty bodies. Every edit it accepted was discarded.
A screen that accepts edits and silently drops them is worse than no screen, so the bridge and the
settings sheet went, and `SettingsPane` in `commonMain` (which reaches the real
`ArchiveAssistantStateStore`, and now persists through `FileAiEngineSettingsRepository`) is the only
settings surface.

`ContentView.swift` (`ComposeRootPreviewView`), `SettingsView.swift` and `SharedStateBridge.swift` are
**still in the target but unreachable** — nothing instantiates them, and each carries a warning at the
top of the file saying what it was. Removing them means deleting their four entries apiece from
`project.pbxproj` (PBXBuildFile, PBXFileReference, PBXGroup children, PBXSourcesBuildPhase), which
cannot be compiled on this machine. A file that does not compile is better than a project file that
cannot be built, so the deletion is a separate, verifiable change rather than a side effect of this one.

`onChooseModelFile = {}` is left inert on purpose: choosing a model file needs a native file picker
that does not exist yet, and a callback that pretends to work is worse than one that is visibly absent.

**What the smoke test can no longer prove, and what the next run is for.** With the shell gone there
is only one screen to photograph, so the two native-shell captures and the `--compose-preview` argument
that reached the old staging surface are both gone: the script now takes three shots — iPhone light,
iPhone dark, iPad light — and checks a single floor, `MIN_DISTINCT=300`, instead of the 25 / 20 pair
that was calibrated to sit *under* the 86- and 81-colour shells. Those old floors had become a check
that passes a dashboard missing 97% of its colours. None of this has been compiled: the Swift changes
are only legible to a macOS toolchain, so the next `Simulator smoke` run is the first evidence that
what gets photographed is the dashboard rather than a shell around it. The iPad capture is still
portrait, for the *Tooling pitfalls* reasons above.

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
- **A Kotlin function type returning `Unit` does not import into Swift as a `Void`-returning closure.**
  `(String?) -> Unit` becomes `(String?) -> KotlinUnit`, so handing it straight to a Swift API that
  declares `@escaping (String?) -> Void` fails to compile with `cannot convert value of type
  '(String?) -> KotlinUnit' to expected argument type '(String?) -> Void'`. Wrap it at the boundary and
  discard the value: `onPicked: { path in _ = callback(path) }`. Changing the Swift signature to
  `-> KotlinUnit` instead drags a Kotlin type into the pure-Swift file and forces `_ =` on every call
  site there.
- **Xcode project object IDs are unique keys, not a sequence.** `project.pbxproj` looks like
  `AAAAAAAAAAAAAAAAAAAA0108`; adding a file by copying a neighbouring line and incrementing the
  suffix produced a duplicate ID shared with `AppDelegate.swift`, and one `PBXFileReference` then
  referred to two files. Pick an ID that the file nowhere defines; the check is cheap and needs no
  Xcode: collect every `^\t\t([A-Z0-9]{24})` definition line and look for repeats plus brace balance.
- **The Kotlin/Native compiler runs in its own JVM, and the plugin silently adds `-Xmx3g`.** The
  Gradle daemon's `org.gradle.jvmargs` does not reach it. KGP 2.2.21 reads `kotlin.native.jvmArgs`
  (deprecated alias `org.jetbrains.kotlin.native.jvmArgs`, split on whitespace) and, in
  `ToolSpec.configureDefaultMaxHeapSize`, appends `-Xmx3g` only when no supplied arg starts with
  `-Xmx`. That 3 GB is not enough for the release framework link: LTO's devirtualization analysis
  died with `java.lang.OutOfMemoryError: Java heap space` at
  `DevirtualizationAnalysis.buildConstraintGraphPrecursor` after 11 minutes, while the *debug*
  framework linked fine — the failure is in the release path only. Set `kotlin.native.jvmArgs=-Xmx4g`
  in `gradle.properties` and be aware this is a runner-memory trade, not a code fix.

