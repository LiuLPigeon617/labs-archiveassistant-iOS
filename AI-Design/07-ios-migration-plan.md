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

**But bundling them changed nothing yet**, and the reason is worth stating plainly: nothing calls
`archiveFontFamily`, and nothing calls `ProvideImperialFonts`. `LocalImperialFonts` still holds its
default:

```kotlin
staticCompositionLocalOf { ImperialFonts(title = FontFamily.Serif, display = FontFamily.Serif) }
```

So the imperial typography has never been installed on **any** platform, including Android — every
pane has been rendering in the platform serif fallback while the fonts sat unused. Installing
`ProvideImperialFonts(fonts)` at the root is the missing half of "fonts look right on iOS", and the
root is exactly what does not exist yet: it belongs to remaining work #5, the real Compose entry
point. Do not treat the font migration as visually complete until that lands.

Fonts reach composables through `LocalImperialFonts` (a CompositionLocal) rather than parameters, so
private helpers do not each need a font argument. `ProvideImperialFonts` installs them.

Material Icons are deliberately **not** a dependency of `:shared`; the iOS chrome uses SF Symbols.
Icon affordances are injected slots (`backIcon`, `settingsIcon`) or drawn locally (see
`CloseSearchGlyph` in HomePane).

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
   migrated yet. Read the size finding below before copying any of it, and note that the fonts have
   no effect until #5 installs `ProvideImperialFonts`.
5. Replace the placeholder hosted by `ComposeHostingViewController` with the real Compose entry
   point, and wire it to `ArchiveAssistantStateStore`. That root must also call
   `ProvideImperialFonts(ImperialFonts(title = archiveFontFamily("san_ji_xing_kai_jian_ti_cu")
   ?: FontFamily.Serif, display = archiveFontFamily("dinglie_song_typeface") ?: FontFamily.Serif))`
   — without it the bundled TTFs never reach a composable.
6. Wire `LiteRT-LM` through its Swift API behind the existing `LocalLlmEngine` interface.
7. Port `ModelDownloadManager` (interface exists; the 513-line OkHttp implementation does not).
8. Implement `DocumentContentExtractor` for iOS via PDFKit (currently a `NoOp` placeholder).
9. Replace the upscaled 512 px app icon with a native 1024 px asset before any App Store submission.

### Artwork size: the remainder is not a free move

The 49 asset names referenced by Kotlin code total **44.9 MB** in `app/src/main/res`, against an
unsigned IPA that currently ships at ~5.2 MB. Copying them verbatim would add roughly 45 MB to the
bundle, so this is a bundling decision, not a mechanical copy.

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

