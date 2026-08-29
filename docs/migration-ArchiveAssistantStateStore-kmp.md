# Migration Plan: `ArchiveAssistantStateStore.kt` → KMP commonMain

Generated from full source analysis. Every Android/JVM/Platform dependency is listed with line
number, exact code, and replacement. See section 8 for the complete line-indexed checklist.

## 0. Summary

The file has **7 top-level platform imports** (lines 3–8, 47): `android.content.Context`,
`android.net.Uri`, `android.util.Log`, `java.io.File`, and three `androidx.compose.runtime`
delegate imports. All platform behavior converges onto two responsibilities:

1. **File/storage access** — `Context.filesDir`, `java.io.File`, reading `res/drawable`+`res/raw`
   assets → **`PlatformFileStore` + `BundledAssetReader`**.
2. **Content-provider byte sources** — `Uri` → **`ContentSource`**.
3. **Logging** — `android.util.Log` → **`Logger`**.

The class is *nearly clean already*: `Context` is only used to derive `filesDir` paths and resolve
assets; `Uri` is only an opaque token forwarded to `ModelDownloadManager.importModel` and
`DocumentContentExtractor.extract`; `File` is only a path container. The bulk of the class is pure
Compose state mutation that needs **zero** platform work.

## 1. Full constructor signature (lines 53–77)

```kotlin
class ArchiveAssistantStateStore(
  private val classifier: MockKnowledgeClassifier = MockKnowledgeClassifier(),
  private val initialState: ArchiveAssistantState =
    ArchiveAssistantState(
      topics = SampleKnowledgeData.topics,
      items = SampleKnowledgeData.items,
      aiSettings = SampleKnowledgeData.defaultAiEngineSettings,
    ),
  private val appDataRepository: AppDataRepository? = null,
  private val aiSettingsRepository: AiEngineSettingsRepository? = null,
  private val localLlmEngine: LocalLlmEngine? = null,
  private val modelDownloadManager: ModelDownloadManager? = null,
  private val inferenceConnection: LocalInferenceGateway? = null,
  private val localModelStateProvider: (() -> LocalModelState)? = null,
  private val localModelFileExists: (() -> Boolean)? = null,
  smartSummarizer: SmartSummarizer? = null,
  private val remoteSmartSummarizerFactory: (AiEngineSettings) -> SmartSummarizer =
    ::RemoteApiSmartSummarizer,
  private val webPageContentFetcher: WebPageContentFetcher = DefaultWebPageContentFetcher(),
  documentContentExtractor: DocumentContentExtractor? = null,
  androidContext: Context? = null,                       // ← platform (73)
  val itemsDirProvider: (() -> File)? = androidContext?.let { context ->
    { File(context.filesDir, "items") }                  // ← platform (74–76)
  },
)
```

**Replacement** (this is the single most impactful change; it ripples through `appContext`,
`itemsDirProvider`, `documentContentExtractor`, `restoreLocalModelState`):

```kotlin
class ArchiveAssistantStateStore(
  ... unchanged params ...
  documentContentExtractor: DocumentContentExtractor? = null,
  private val fileStore: PlatformFileStore = platformFileStore(),
  private val bundledAssetReader: BundledAssetReader = BundledAssetReader(),
  private val logger: Logger = platformLogger(),
  private val itemsDirProvider: (() -> String)? = { fileStore.itemsDir },
)
```

> ⚠️ `ModelDownloadManager.importModel(uri: Uri)` and `DocumentContentExtractor.extract(uri, …)`
> are interfaces defined outside this file that also carry `Uri`. They must be widened in parallel
> (→ `ContentSource`) or commonMain won't compile. See §7.

## 2. Exhaustive platform-dependency inventory

### 2.1 Imports

| Line | Code | Action |
|---|---|---|
| 3 | `import android.content.Context` | Remove |
| 4 | `import android.net.Uri` | Remove |
| 5 | `import android.util.Log` | Remove |
| 6 | `import androidx.compose.runtime.getValue` | **KEEP** (KMP-safe) |
| 7 | `import androidx.compose.runtime.mutableStateOf` | **KEEP** (KMP-safe) |
| 8 | `import androidx.compose.runtime.setValue` | **KEEP** (KMP-safe) |
| 47 | `import java.io.File` | Remove |

### 2.2 `android.content.Context`

`Context` is captured once at line 78 into `appContext`, then used at the lines below.

| Line | Code | Replacement |
|---|---|---|
| 73 | `androidContext: Context? = null` | `fileStore` + `bundledAssetReader` + `logger` params |
| 78 | `private val appContext = androidContext` | delete field |
| 80 | `documentContentExtractor ?: androidContext?.let(::DefaultDocumentContentExtractor)` | rebuild default extractor context-free (see §4.2) |
| 260 | `val context = appContext ?: return state` | `if (itemsDirProvider == null) return state` |
| 312 | `File(context.filesDir, "items").also { it.mkdirs() }` | `val itemsDir = itemsDirProvider?.invoke() ?: return state` (String) |
| 320 | `context.resources.getIdentifier(resource.name, resource.type, context.packageName)` | drop — `BundledAssetReader` encodes asset identity |
| 322 | `context.resources.openRawResource(resId)` | `bundledAssetReader.materialize(...)` |
| 1272 | `private fun localModelFile(): File? = appContext?.let { ... File(context.filesDir, "models/...") }` | `private fun localModelPath(): String? = fileStore.modelsDir + "/" + GEMMA_4_E4B_IT.fileName` |
| 1273 | `File(context.filesDir, ...)` | (same as above) |
| 1285 | `(appContext != null \|\| state.localModelState.modelPath != null)` | `(localModelPath() != null \|\| state.localModelState.modelPath != null)` |

### 2.3 `android.net.Uri`

| Line | Code | Replacement |
|---|---|---|
| 4 | `import android.net.Uri` | remove (co-changes in §7) |
| 1159 | `fun importLocalModel(uri: Uri)` | `fun importLocalModel(source: ContentSource)` |
| 1167 | `manager.importModel(GEMMA_4_E4B_IT, uri)` | `manager.importModel(GEMMA_4_E4B_IT, source)` |
| 1243 | `Uri.parse(it)` | drop — carry `clipboardSourceUri` as a materialized path string |
| 1347 | `val uri: Uri` (in `DocumentSummarizeSource`) | `val source: ContentSource` or `val sourcePath: String` |
| 787 | `extractor.extract(source.uri, source.format, source.fileName)` | `extractor.extract(source.source, source.format, source.fileName)` |

### 2.4 `android.util.Log`

| Line | Code | Replacement |
|---|---|---|
| 179 | `Log.w(TAG, "模型版本已变更，请重新下载")` | `logger.w(TAG, "模型版本已变更，请重新下载")` |

This is the **only** `Log` call in the whole file. `TAG` (line 1336) stays.

### 2.5 `java.io.File`

| Line | Code | Replacement |
|---|---|---|
| 47 | `import java.io.File` | remove |
| 74–76 | `itemsDirProvider: (() -> File)? = { File(context.filesDir, "items") }` | `itemsDirProvider: (() -> String)? = { fileStore.itemsDir }` |
| 194 | `modelPath = modelFile?.absolutePath` | `modelPath = localModelPath()` |
| 312 | `File(itemsDir, resource.outputFileName)` | `fileStore.itemsDir + "/" + resource.outputFileName` |
| 317 | `File(itemsDir, resource.outputFileName)` | (same) |
| 318 | `if (!dest.exists())` | `if (!fileStore.exists(destPath))` |
| 322–324 | `dest.outputStream().use { ... }` | handled inside `BundledAssetReader.materialize` |
| 327 | `item.copy(sourceUrl = dest.absolutePath)` | `item.copy(sourceUrl = destPath)` |
| 866 | `generatedMarkdownFile?.absolutePath` | `generatedMarkdownPath` |
| 879 | `fileName = generatedMarkdownFile?.name` | `generatedMarkdownPath?.substringAfterLast('/')` |

### 2.6 `androidx.compose.runtime` — FLAGGED, allowed in KMP

| Line | Code | Status |
|---|---|---|
| 6–8 | imports | **KEEP** |
| 85 | `var state: ArchiveAssistantState by mutableStateOf(resolveMockResourcePaths(initialState))` | **KEEP** (only delegate usage) |

**Caveat:** valid in commonMain only if `shared` already depends on multiplatform compose-runtime;
otherwise add that dependency. The `resolveMockResourcePaths(...)` call in the initializer is a
suspend call after migration and must move into `init { scope.launch { ... } }` (see §3).

### 2.7 `writeSmartMarkdownDocument` (lines 1258–1261) + `writeMarkdownFile`

| Line | Code | Replacement |
|---|---|---|
| 1258 | `private fun writeSmartMarkdownDocument(...): File?` | `private suspend fun ...: String?` |
| 1259 | `val itemsDir = itemsDirProvider?.invoke() ?: return null` | unchanged (now String) |
| 1260 | `return writeMarkdownFile(itemsDir, title, content)` | inline via `fileStore.writeBytes(path, content.encodeToByteArray())` |

`writeMarkdownFile(itemsDir: File, …)` lives in `UriImportHelper.kt:49` (JVM-only). Either inline
its logic (mkdirs + unique-name loop + writeText) against `PlatformFileStore`, or extract the pure
string helpers (`markdownFileName` at line 36 is already pure; `uniqueImportFile` needs a string
port). Recommend: inline, since it's tiny.

### 2.8 Other JVM/Android-only APIs

| Line | Code | Verdict |
|---|---|---|
| 495, 612, 845, 934 | `System.currentTimeMillis()` | **replace** with `kotlin.time.Clock.System.now().toEpochMilliseconds()` (or `kotlin.system.getTimeMillis()`); breaks iOS native otherwise |
| 48–51 | `kotlinx.coroutines.*` | KMP-safe, keep |
| 1336 | `const val TAG` | keep |
| 1342–1344 | `: Exception(...)` | keep (kotlin.Exception) |
| — | `String.format`, `Locale`, reflection, `java.time`, `java.util`, `SimpleDateFormat` | **None present** |

## 3. `resolveMockResourcePaths()` (lines 259–330) — the 23-asset copy

**Current mechanism:**
1. Line 260: bail if `appContext == null`.
2. Lines 261–311: hardcoded `Map<itemId, MockResource(name, type, outputFileName)>` with 23
   entries. `type` is `"drawable"` (6 PNGs) or `"raw"` (17 `.md`/`.pdf`).
3. Line 312: `File(context.filesDir, "items").also { it.mkdirs() }`.
4. Lines 315–328: for each mapped item, if `!dest.exists()`, `getIdentifier(name, type, pkg)` →
   `openRawResource(resId)` → copy to `dest.outputStream()`; then `item.copy(sourceUrl = dest.absolutePath)`.

Net effect: materialize each bundled asset into `filesDir/items/<outputFileName>` once, then set
`sourceUrl` to the local absolute path.

**Replacement** — `BundledAssetReader.materialize(assetName, outputFileName): String?` models this
exactly. The whole method collapses to:

```kotlin
private suspend fun resolveMockResourcePaths(state: ArchiveAssistantState): ArchiveAssistantState {
  return state.copy(
    items = state.items.map { item ->
      val resource = MOCK_RESOURCE_BY_ITEM_ID[item.id] ?: return@map item
      val localPath = bundledAssetReader.materialize(resource.assetName, resource.outputFileName)
        ?: return@map item
      item.copy(sourceUrl = localPath)
    }
  )
}
```

Notes:
- The `drawable`/`raw` `type` distinction collapses into a single `assetName`; the Android actual
  of `BundledAssetReader` already resolves `res/drawable` vs `res/raw`. **Drop `MockResource.type`**.
- The method becomes `suspend` (materialize is suspend). Call sites must move:
  - **Line 85** (field initializer) cannot call suspend. Initialize `state` with raw `initialState`,
    then in `init {}` (line 93) do `scope.launch { state = resolveMockResourcePaths(state) }`.
  - **Line 121** (`loadPersistedStateAsync`) is already inside `scope.launch` — fine, just add
    `suspend` to the helper.
- Extract the 23-entry map to a top-level common `val` `MOCK_RESOURCE_BY_ITEM_ID` with
  `MockResource(assetName, outputFileName)` for testability.

## 4. Uri flow deep-dives

### 4.1 `importLocalModel(uri: Uri)` — lines 1159–1169
Public platform-typed API. Platform picker hands a file URI. Migrate to `ContentSource`:
`fun importLocalModel(source: ContentSource)`; body calls `manager.importModel(GEMMA_4_E4B_IT, source)`.
Co-change `ModelDownloadManager.importModel` (interface line 39, impl line 150 currently does
`context.contentResolver.openInputStream(uri)` at line 163) → use `source.openRead()` + `source.displayName`.

### 4.2 `documentSummarizeSource()` (1240–1249) + `DocumentSummarizeSource` (1346–1350)
Line 1243 `Uri.parse(it)` round-trips a stored clipboard URI string through `android.net.Uri` —
the crux. Recommended fix: **stop parsing a Uri**. Treat `state.clipboardSourceUri` (a String) as a
materialized path/handle; change `DocumentSummarizeSource` field to `sourcePath: String` (or
`source: ContentSource` if the extractor genuinely needs ContentResolver for streaming). Platform
clipboard layer must materialize before calling `showClipboard(...)`.

### 4.3 `DocumentContentExtractor` co-change
Line 787 passes `source.uri` to `extractor.extract(...)`. That interface (used at lines 12, 15, 72,
79, 80) must widen its `Uri` param to `ContentSource`; `DefaultDocumentContentExtractor` becomes
context-free (reads `openRead()` bytes instead of ContentResolver).

## 5. `itemsDirProvider` default (lines 74–76)

- Defaults to **null** if `androidContext` is null; otherwise closes over context →
  `File(context.filesDir, "items")`.
- Only consumer: `writeSmartMarkdownDocument` (line 1259), nullable → null disables markdown writing.
- Migration: `itemsDirProvider: (() -> String)? = { fileStore.itemsDir }`. Android UI callers passing
  a `File`-returning lambda must switch to `String` or rely on the default.
- `fileStore` must be injected regardless — `localModelPath()` uses `fileStore.modelsDir` independently.

## 6. Method classification

### Platform-touching (must change)
| Method | Lines | Platform surface |
|---|---|---|
| constructor | 53–77 | `Context`, `File` |
| init | 93–97 | transitively `resolveMockResourcePaths`/`restoreLocalModelState` |
| `resolveMockResourcePaths` | 259–330 | `Context`, `File`, res assets (heaviest) |
| `restoreLocalModelState` | 177–199 | `localModelFile()` (184, 194) |
| `loadPersistedStateAsync` | 99–134 | calls `resolveMockResourcePaths` (121) |
| `importLocalModel` | 1159–1169 | `Uri` |
| `documentSummarizeSource` | 1240–1249 | `Uri.parse` |
| `writeSmartMarkdownDocument` | 1258–1261 | `File`, `writeMarkdownFile` |
| `localModelFile` | 1272–1274 | `File(context.filesDir, "models/…")` |
| `shouldValidateLocalModelFileBeforeStart` | 1283–1285 | `appContext != null` |

### Pure state manipulation (no platform need — keep as-is)
The dominant bulk. `saveData`(136), `persistData`(143), `normalizeItemTopicIds`(147),
`mergeBuiltInSampleItems`(154), `saveAiSettings`(169), `updateDownloadModelState`(214),
`updateInferenceModelState`(230), `updateLocalModelState`(234), `serviceOwnedStatuses`(251),
`closePanes`(356)–`closeTopicNameDialog`(415), `openDeleteConfirmDialog`(432),
`confirmDeleteTopic`(529), `deleteItem`(535), `openAddItemDialog`(444)–`confirmEditItem`(625),
`openTopic`(627), `updateParserInput`(640), `updateHomeSearchQuery`(649), `submitParserInput`(653),
`summarizeParserInput`(657), `summarizeAndSave`(695), `summarizeRawInput`(712),
`createSmartSummarizeRequest`(748), `createDocumentSmartSummarizeRequest`(781 — except extract at
787), `handleSmartSummarizeResult`(811), `saveSmartSummarizeSuccess`(826 — except 859–879),
`handleParserClassificationResult`(920), `classifyParserInput`(965), `rejectTopicCrud`(994),
`selectFilter`(998), `openCardModal`/`closeCardModal`(1002/1007), clipboard block `showClipboard`
(1015)–`clipboardAddItemPrefill`(1333), `updateAiSettings`(1127), `downloadModel`(1144),
`cancelDownload`(1152), `startModel`(1171), `stopModel`(1201), `updateBackendPreference`(1206),
`runBenchmark`(1210), `topicTitleValidationMessage`(1224), `extractSourceUrl`(1231),
`isBareWebUrl`(1263), `isDocumentOnlyClipboardInput`(1251), `resetLocalModelState`(1279),
`currentClipboardSnapshot`(1287), companions(1335–1340), exceptions(1342–1344).

## 7. Required co-changes outside this file

1. `ModelDownloadManager.importModel(model, uri: Uri)` — interface(39) + `OkHttpModelDownloadManager`(150). `Uri → ContentSource`.
2. `DocumentContentExtractor.extract(uri, format, fileName)` — interface + `DefaultDocumentContentExtractor`. `Uri → ContentSource`.
3. `DefaultDocumentContentExtractor` (built at line 80 with context) — make context-free.
4. `writeMarkdownFile(itemsDir: File, …)` in `UriImportHelper.kt:49` — inline into `PlatformFileStore` or extract pure string helpers.
5. UI callers of `importLocalModel(Uri)` → pass `ContentSource`; clipboard producers materialize before `showClipboard`.
6. `shared/build.gradle` — add `compose-runtime` dep if absent (for `mutableStateOf`).
7. `PlatformFileStore` — `itemsDir`/`modelsDir` are `String`; guarantee actual creates dirs (or add a mkdirs helper).

## 8. Complete replacement checklist (line-indexed)

| Line(s) | Action |
|---|---|
| 3 | remove `Context` import |
| 4 | remove `Uri` import |
| 5 | remove `Log` import |
| 6–8 | keep compose-runtime imports |
| 47 | remove `File` import |
| 73 | replace `androidContext` with `fileStore` + `bundledAssetReader` + `logger` |
| 74–76 | `itemsDirProvider: (() -> String)? = { fileStore.itemsDir }` |
| 78 | delete `appContext` field |
| 80 | rebuild default extractor context-free |
| 85 | init state w/o suspend; move `resolveMockResourcePaths` into `init` launch |
| 93–97 | add asset-materialization launch |
| 121 | keep (in coroutine); add `suspend` to helper |
| 179 | `Log.w` → `logger.w` |
| 184 | `localModelFile()` → `localModelPath()` |
| 194 | `modelFile?.absolutePath` → `localModelPath()` |
| 259–330 | collapse via `BundledAssetReader.materialize`; extract map; drop `type` |
| 495/612/845/934 | `System.currentTimeMillis()` → KMP clock |
| 787 | `source.uri` → `source.source` (ContentSource) |
| 859–879 | `File?` markdown → `String?` path |
| 1159–1169 | `importLocalModel(Uri)` → `importLocalModel(ContentSource)` |
| 1243 | drop `Uri.parse` |
| 1258–1261 | `writeSmartMarkdownDocument` → `fileStore.writeBytes` |
| 1272–1274 | `localModelFile` → `localModelPath` via `fileStore.modelsDir` |
| 1285 | `appContext != null` → `localModelPath() != null` |
| 1346–1350 | `DocumentSummarizeSource.uri: Uri` → string path or `ContentSource` |
