package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.io.File
import java.nio.file.Files

class BookAssemblerTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-book").toFile()

    private fun page(dir: File, name: String, content: String): File {
        return File(dir, name).apply { writeText(content) }
    }

    @Test
    fun `assembler merges ordered pages into a single AsciiDoc document`() {
        val dir = tempDir()
        page(dir, "001-page.adoc", "== Chapter 1\n\nFirst page content.")
        page(dir, "002-page.adoc", "== Chapter 2\n\nSecond page content.")
        page(dir, "003-page.adoc", "== Chapter 3\n\nThird page content.")

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Test Book",
            author = "Test Author",
        )

        assertTrue(result.content.contains("= Test Book"), "the book title must be present")
        assertTrue(result.content.contains("Chapter 1"), "page 1 content must be present")
        assertTrue(result.content.contains("Chapter 2"), "page 2 content must be present")
        assertTrue(result.content.contains("Chapter 3"), "page 3 content must be present")
        assertEquals(3, result.pageCount, "three pages must be merged")
    }

    @Test
    fun `assembler preserves the order of pages by numeric prefix`() {
        val dir = tempDir()
        page(dir, "003-page.adoc", "== Chapter 3")
        page(dir, "001-page.adoc", "== Chapter 1")
        page(dir, "002-page.adoc", "== Chapter 2")

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Ordered Book",
            author = "Author",
        )

        val firstIdx = result.content.indexOf("Chapter 1")
        val secondIdx = result.content.indexOf("Chapter 2")
        val thirdIdx = result.content.indexOf("Chapter 3")
        assertTrue(firstIdx < secondIdx, "page 1 must come before page 2")
        assertTrue(secondIdx < thirdIdx, "page 2 must come before page 3")
    }

    @Test
    fun `assembler orders non-numbered pages after numbered ones`() {
        val dir = tempDir()
        page(dir, "001-page.adoc", "== Chapter 1")
        page(dir, "appendix.adoc", "== Appendix")
        page(dir, "002-page.adoc", "== Chapter 2")

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Mixed Book",
            author = "Author",
        )

        val chapter2Idx = result.content.indexOf("Chapter 2")
        val appendixIdx = result.content.indexOf("Appendix")
        assertTrue(chapter2Idx < appendixIdx, "non-numbered pages must come after numbered ones")
    }

    @Test
    fun `assembler includes the author as document attribute`() {
        val dir = tempDir()
        page(dir, "001-page.adoc", "== Chapter 1\n\nContent.")

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Authored Book",
            author = "Jane Doe",
        )

        assertTrue(result.content.contains(":author: Jane Doe"), "the author attribute must be set")
    }

    @Test
    fun `assembler produces an empty book when no pages are present`() {
        val dir = tempDir()

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Empty Book",
            author = "Nobody",
        )

        assertTrue(result.content.contains("= Empty Book"), "the title must still be present")
        assertEquals(0, result.pageCount, "no pages must be merged")
        assertEquals(0, result.photoCount, "no photos must be referenced")
    }

    @Test
    fun `assembler does not mutate any source page file`() {
        val dir = tempDir()
        val pageFile = page(dir, "001-page.adoc", "== Chapter 1\n\nOriginal content.")
        val original = pageFile.readText()

        BookAssembler.assemble(
            pagesDir = dir,
            title = "Immutable Book",
            author = "Author",
        )

        assertEquals(original, pageFile.readText(), "the source page file must not be modified")
    }

    @Test
    fun `assembler ignores non-adoc files in the pages directory`() {
        val dir = tempDir()
        page(dir, "001-page.adoc", "== Chapter 1")
        File(dir, "notes.txt").writeText("not a page")
        File(dir, "image.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50))

        val result = BookAssembler.assemble(
            pagesDir = dir,
            title = "Filtered Book",
            author = "Author",
        )

        assertEquals(1, result.pageCount, "only .adoc files must be merged")
        assertTrue(!result.content.contains("not a page"), "non-adoc content must be excluded")
    }

    @Test
    fun `assembler embeds matching photos as image directives`() {
        val pagesDir = tempDir()
        val photosDir = tempDir()
        page(pagesDir, "001-page.adoc", "== Chapter 1\n\nFirst page.")
        File(photosDir, "001-page.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))

        val result = BookAssembler.assemble(
            pagesDir = pagesDir,
            title = "Photo Book",
            author = "Author",
            photosDir = photosDir,
        )

        assertEquals(1, result.photoCount, "one photo must be referenced")
        assertTrue(result.content.contains("image::001-page.png[]"), "the photo image directive must be present")
    }

    @Test
    fun `assembler counts zero photos when no matching photo is found`() {
        val pagesDir = tempDir()
        val photosDir = tempDir()
        page(pagesDir, "001-page.adoc", "== Chapter 1\n\nFirst page.")

        val result = BookAssembler.assemble(
            pagesDir = pagesDir,
            title = "No Photo Book",
            author = "Author",
            photosDir = photosDir,
        )

        assertEquals(0, result.photoCount, "no photo must be referenced")
        assertTrue(!result.content.contains("image::"), "no image directive must be present")
    }

    @Test
    fun `assembler matches photos across supported extensions`() {
        val pagesDir = tempDir()
        val photosDir = tempDir()
        page(pagesDir, "001-page.adoc", "== Chapter 1")
        File(photosDir, "001-page.jpg").writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))

        val result = BookAssembler.assemble(
            pagesDir = pagesDir,
            title = "Jpg Book",
            author = "Author",
            photosDir = photosDir,
        )

        assertEquals(1, result.photoCount, "the jpg photo must be referenced")
        assertTrue(result.content.contains("image::001-page.jpg[]"), "the jpg image directive must be present")
    }
}