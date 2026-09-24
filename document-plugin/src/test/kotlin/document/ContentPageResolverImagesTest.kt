package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TDD anchors for the image-aware [ContentPageResolver] (EPIC DOC-BOOK-IMAGES,
 * code-review S-258 B4/B5).
 *
 * The resolver is the integration point of the two domain objects:
 * - [BookImageRewriter] always repairs ghost `image::` directives (EPUB
 *   correctness);
 * - [BookImageResolver] + `photosDir` emit the page scan as an illustration
 *   (opt-in — the whole scanned corpus is 525 MB, never embedded by default).
 */
class ContentPageResolverImagesTest {

    private fun scans(dir: File) = dir.resolve("scans").apply { mkdirs() }

    @Test
    fun `content keeps the exact behaviour when photosDir is absent (non-regression)`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("014.adoc").writeText("Historique content [ILLISIBLE]")
        scans.resolve("014.jpg").writeBytes(byteArrayOf(1))

        val resolver = ContentPageResolver(scans)
        val section = BookSection(ref = "1.0.2", title = "Historique", page = 14, pdfFile = "014.pdf")
        val content = resolver.content(section)
        assertTrue("Historique content" in content, "the OCR body must be preserved")
        assertFalse("image::" in content, "no illustration must be emitted without photosDir")
    }

    @Test
    fun `content illustrates each page with its scan when photosDir is configured`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("014.adoc").writeText("Historique content")
        scans.resolve("014.jpg").writeBytes(byteArrayOf(1))

        val resolver = ContentPageResolver(scans, photosDir = scans)
        val section = BookSection(ref = "1.0.2", title = "Historique", page = 14, pdfFile = "014.pdf")
        val content = resolver.content(section)
        assertTrue("image::014.jpg[]" in content, "the page scan must be referenced, got:\n$content")
    }

    @Test
    fun `content repairs a ghost with the page scan even without photosDir`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("157.adoc").writeText("== Section\nimage::qr-code.png[QR Code]\nSuite")
        scans.resolve("157.jpg").writeBytes(byteArrayOf(1))

        val resolver = ContentPageResolver(scans)
        val section = BookSection(ref = "4.3.2", title = "Vocabulaire", page = 157, pdfFile = "157.pdf")
        val content = resolver.content(section)
        assertTrue("image::157.jpg[QR Code]" in content, "the ghost must be repaired, got:\n$content")
        assertFalse("qr-code.png" in content, "the ghost target must disappear, got:\n$content")
    }

    @Test
    fun `content drops a ghost when the page has no scan`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("042.adoc").writeText("== Section\nimage::ghost.png[]\nSuite")

        val resolver = ContentPageResolver(scans)
        val section = BookSection(ref = "1.1", title = "Section", page = 42, pdfFile = "042.pdf")
        val content = resolver.content(section)
        assertFalse("image::" in content, "the ghost must be dropped, got:\n$content")
        assertTrue("Suite" in content, "the surrounding text must survive, got:\n$content")
    }

    @Test
    fun `content does not duplicate the scan when the ghost repaired to the same file`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("157.adoc").writeText("== Section\nimage::qr-code.png[QR Code]\nSuite")
        scans.resolve("157.jpg").writeBytes(byteArrayOf(1))

        val resolver = ContentPageResolver(scans, photosDir = scans)
        val section = BookSection(ref = "4.3.2", title = "Vocabulaire", page = 157, pdfFile = "157.pdf")
        val content = resolver.content(section)
        assertEquals(1, "image::157.jpg".toRegex().findAll(content).count(), "the scan must be referenced exactly once, got:\n$content")
    }

    @Test
    fun `content concatenates a split page with its own split scan`(@TempDir dir: File) {
        val scans = scans(dir)
        scans.resolve("055.adoc").writeText("Body page 55")
        scans.resolve("055_2.adoc").writeText("Continuation of page 55")
        scans.resolve("55.jpg").writeBytes(byteArrayOf(1))
        scans.resolve("55_2.jpg").writeBytes(byteArrayOf(1))

        val resolver = ContentPageResolver(scans, photosDir = scans)
        val section = BookSection(ref = "1.1.2", title = "Differencier", page = 55, pdfFile = "055.pdf")
        val content = resolver.content(section)
        assertTrue("Body page 55" in content, "primary page content must be present")
        assertTrue("Continuation of page 55" in content, "split content must be present")
        assertTrue("image::55.jpg[]" in content, "primary split scan must be referenced, got:\n$content")
        assertTrue("image::55_2.jpg[]" in content, "continuation split scan must be referenced, got:\n$content")
    }
}
