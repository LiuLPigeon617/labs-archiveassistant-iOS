package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.ContentSource
import com.lyihub.archiveassistant.platform.PlatformFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the file-store half of DetailPane's Android seams.
 *
 * The two seams under test here (`copyUriToFile` and `writeMarkdownPrefillFile`) had no platform
 * half at all: only `Context` was Android-specific, the work itself was "read bytes, find a free
 * name, write bytes". That work is what this test pins down, on the one target that runs locally.
 *
 * The picker and the image decoder are deliberately absent: both are `expect`/`actual` over real
 * platform APIs (BitmapFactory, skiko, a system file dialog), so a fake of them would only test the
 * fake. What can be tested without a device is the naming and collision behaviour, and that is here.
 */
class ContentMaterializationTest {

  private class FakeFileStore : PlatformFileStore {
    val contents = mutableMapOf<String, String>()

    override val itemsDir: String = "/items"
    override val modelsDir: String = "/models"

    override suspend fun exists(path: String): Boolean = path in contents

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
      contents[path] = bytes.decodeToString()
    }

    override suspend fun readBytes(path: String): ByteArray? = contents[path]?.encodeToByteArray()
  }

  private class FakeContentSource(
    override val displayName: String?,
    private val bytes: ByteArray?,
  ) : ContentSource {
    override val sourceKey: String = "fake://source"

    override suspend fun openRead(): ByteArray? = bytes
  }

  private fun importerOver(store: FakeFileStore) = FileStoreContentImporter(store)

  // ContentImporter.materialize

  @Test
  fun materializeCopiesBytesIntoTheDirectoryUnderTheDisplayName() = runTest {
    val store = FakeFileStore()
    val source = FakeContentSource("report.pdf", "pdf-bytes".encodeToByteArray())

    val path = importerOver(store).materialize(source, store.itemsDir)

    assertEquals("/items/report.pdf", path)
    assertEquals("pdf-bytes", store.contents["/items/report.pdf"])
  }

  @Test
  fun materializeReturnsNullWhenTheSourceCannotBeRead() = runTest {
    val store = FakeFileStore()
    val source = FakeContentSource("report.pdf", null)

    assertNull(importerOver(store).materialize(source, store.itemsDir))
    assertEquals(emptyMap(), store.contents)
  }

  @Test
  fun materializeStripsADirectoryPrefixFromTheDisplayName() = runTest {
    val store = FakeFileStore()
    val source =
      FakeContentSource("/storage/emulated/0/Download/report.pdf", "x".encodeToByteArray())

    assertEquals("/items/report.pdf", importerOver(store).materialize(source, store.itemsDir))
  }

  @Test
  fun materializeFallsBackToTheExtensionHintWhenTheNameIsBlank() = runTest {
    val store = FakeFileStore()
    val source = FakeContentSource("   ", "x".encodeToByteArray())

    // The hint is derived from the display name, so a blank name has none either.
    assertEquals("/items/imported-file", importerOver(store).materialize(source, store.itemsDir))
  }

  @Test
  fun materializeKeepsTheExtensionOfTheDisplayNameAsItsFallback() = runTest {
    val store = FakeFileStore()
    val source = FakeContentSource("C:\\tmp\\扫描件.PNG", "x".encodeToByteArray())

    assertEquals("/items/扫描件.PNG", importerOver(store).materialize(source, store.itemsDir))
  }

  @Test
  fun materializeNeverOverwritesAnEarlierImport() = runTest {
    val store = FakeFileStore()
    val importer = importerOver(store)

    val first = importer.materialize(FakeContentSource("photo.png", "one".encodeToByteArray()), store.itemsDir)
    val second = importer.materialize(FakeContentSource("photo.png", "two".encodeToByteArray()), store.itemsDir)

    assertEquals("/items/photo.png", first)
    assertEquals("/items/photo (1).png", second)
    assertEquals("one", store.contents["/items/photo.png"])
    assertEquals("two", store.contents["/items/photo (1).png"])
  }

  @Test
  fun materializeWritesIntoWhateverDirectoryItIsGiven() = runTest {
    val store = FakeFileStore()
    val source = FakeContentSource("note.txt", "hi".encodeToByteArray())

    assertEquals("/models/note.txt", importerOver(store).materialize(source, store.modelsDir))
  }

  // writeMarkdownPrefill

  @Test
  fun writeMarkdownPrefillNamesTheFileAfterTheTitle() = runTest {
    val store = FakeFileStore()

    val path = writeMarkdownPrefill(store, store.itemsDir, "会议纪要", "正文")

    assertEquals("/items/会议纪要.md", path)
    assertEquals("正文", store.contents["/items/会议纪要.md"])
  }

  @Test
  fun writeMarkdownPrefillUsesOnlyTheFirstTitleLine() = runTest {
    val store = FakeFileStore()

    assertEquals(
      "/items/标题.md",
      writeMarkdownPrefill(store, store.itemsDir, "标题\n第二行", "正文"),
    )
  }

  @Test
  fun writeMarkdownPrefillNeverOverwritesAnExistingNote() = runTest {
    val store = FakeFileStore()

    val first = writeMarkdownPrefill(store, store.itemsDir, "笔记", "第一版")
    val second = writeMarkdownPrefill(store, store.itemsDir, "笔记", "第二版")

    assertEquals("/items/笔记.md", first)
    assertEquals("/items/笔记 (1).md", second)
    assertEquals("第一版", store.contents["/items/笔记.md"])
    assertEquals("第二版", store.contents["/items/笔记 (1).md"])
  }

  @Test
  fun writeMarkdownPrefillRefusesABlankDirectory() = runTest {
    val store = FakeFileStore()

    assertNull(writeMarkdownPrefill(store, "   ", "笔记", "正文"))
    assertEquals(emptyMap(), store.contents)
  }
}
