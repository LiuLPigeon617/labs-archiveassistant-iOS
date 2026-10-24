package com.lyihub.archiveassistant.data

import com.lyihub.archiveassistant.platform.PlatformFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Covers the naming half of file import.
 *
 * This is the port of `app/src/main/java/com/lyihub/archiveassistant/data/UriImportHelper.kt` that
 * can actually be verified on this machine: the Android original only fails on Kotlin/Native (via
 * `lineSequence`), so a desktop-only test here is what keeps the shared copy honest.
 */
class ImportNamingTest {

  private class FakeFileStore(initial: Set<String> = emptySet()) : PlatformFileStore {
    val files = initial.toMutableSet()

    override val itemsDir: String = "/items"
    override val modelsDir: String = "/models"

    override suspend fun exists(path: String): Boolean = path in files

    override suspend fun writeBytes(path: String, bytes: ByteArray) {
      files += path
    }

    override suspend fun readBytes(path: String): ByteArray? = null
  }

  // importFileName

  @Test
  fun importFileNameFallsBackWhenDisplayNameIsNull() {
    assertEquals("imported-file.pdf", importFileName(null, ".pdf"))
  }

  @Test
  fun importFileNameFallsBackWhenDisplayNameIsBlank() {
    assertEquals("imported-file.txt", importFileName("   ", ".txt"))
  }

  @Test
  fun importFileNameKeepsAPlainName() {
    assertEquals("photo.png", importFileName("photo.png", ".png"))
  }

  @Test
  fun importFileNameDropsAPosixDirectoryPrefix() {
    assertEquals(
      "report.pdf",
      importFileName("/storage/emulated/0/Download/report.pdf", ".pdf"),
    )
  }

  @Test
  fun importFileNameDropsAWindowsDirectoryPrefix() {
    assertEquals("notes.txt", importFileName("C:\\Users\\me\\notes.txt", ".txt"))
  }

  @Test
  fun importFileNameReplacesNulBytes() {
    assertEquals("a_b.txt", importFileName("a\u0000b.txt", ".txt"))
  }

  // markdownFileName

  @Test
  fun markdownFileNameUsesOnlyTheFirstLine() {
    assertEquals("标题第一行.md", markdownFileName("标题第一行\n第二行"))
  }

  @Test
  fun markdownFileNameFallsBackForABlankTitle() {
    assertEquals("clipboard-note.md", markdownFileName("   "))
  }

  @Test
  fun markdownFileNameReplacesIllegalCharacters() {
    assertEquals(
      "a-b-c-d-e-f-g-h-i.md",
      markdownFileName("a/b:c*d?e\"f<g>h|i"),
    )
  }

  @Test
  fun markdownFileNameDoesNotDoubleTheExtension() {
    assertEquals("already.md", markdownFileName("already.md"))
    assertEquals("ALREADY.MD", markdownFileName("ALREADY.MD"))
  }

  @Test
  fun markdownFileNameTruncatesLongTitlesTo48Characters() {
    assertEquals("x".repeat(48) + ".md", markdownFileName("x".repeat(60)))
  }

  // uniqueImportPath / pathIn

  @Test
  fun pathInToleratesTrailingSeparators() {
    assertEquals("/items/a.txt", pathIn("/items/", "a.txt"))
    assertEquals("/items/a.txt", pathIn("/items", "a.txt"))
    assertEquals("items/a.txt", pathIn("items\\", "a.txt"))
  }

  @Test
  fun uniqueImportPathReturnsTheOriginalWhenFree() = runTest {
    val store = FakeFileStore()
    assertEquals("/items/a.txt", uniqueImportPath(store, store.itemsDir, "a.txt"))
  }

  @Test
  fun uniqueImportPathAppendsASuffixOnCollision() = runTest {
    val store = FakeFileStore(setOf("/items/a.txt"))
    assertEquals("/items/a (1).txt", uniqueImportPath(store, store.itemsDir, "a.txt"))
  }

  @Test
  fun uniqueImportPathSkipsEveryTakenSuffix() = runTest {
    val store = FakeFileStore(setOf("/items/a.txt", "/items/a (1).txt", "/items/a (2).txt"))
    assertEquals("/items/a (3).txt", uniqueImportPath(store, store.itemsDir, "a.txt"))
  }

  @Test
  fun uniqueImportPathHandlesNamesWithoutAnExtension() = runTest {
    val store = FakeFileStore(setOf("/items/README"))
    assertEquals("/items/README (1)", uniqueImportPath(store, store.itemsDir, "README"))
  }

  @Test
  fun uniqueImportPathKeepsOnlyTheLastExtension() = runTest {
    val store = FakeFileStore(setOf("/items/archive.tar.gz"))
    assertEquals("/items/archive.tar (1).gz", uniqueImportPath(store, store.itemsDir, "archive.tar.gz"))
  }

  @Test
  fun uniqueImportPathSeesFilesWrittenEarlierThroughTheSameStore() = runTest {
    val store = FakeFileStore()
    val first = uniqueImportPath(store, store.itemsDir, "note.md")
    store.writeBytes(first, "hello".encodeToByteArray())

    val second = uniqueImportPath(store, store.itemsDir, "note.md")
    assertEquals("/items/note.md", first)
    assertEquals("/items/note (1).md", second)
  }
}
