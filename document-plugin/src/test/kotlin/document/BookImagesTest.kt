package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TDD anchors for the image-aware book assembly (EPIC DOC-BOOK-IMAGES).
 *
 * Two pure domain objects are introduced:
 *
 *  1. [BookImageResolver] — resolves the original scan (photo) of a book page
 *     by its *page number*, the image counterpart of [ContentPageResolver]
 *     (which resolves the OCR-ed AsciiDoc by page number). The scanned corpus
 *     stores the scan next to the `.adoc` (unpadded `61.jpg`, split `83_1.jpg`,
 *     padded `007.png`), so both paddings and the usual image extensions are
 *     probed.
 *  2. [BookImageRewriter] — repairs the ghost `image::` directives produced by
 *     OCR (a caption was recognised but no file exists): the directive is
 *     substituted with the page's own scan when one exists, or dropped so the
 *     EPUB stays epubcheck-valid (RSC-007).
 *
 * Both are pure, deterministic, and fully unit-testable (no Gradle, no
 * TestKit) — baby-step TDD before wiring into [AssembleBookTask].
 */
class BookImagesTest {

    @Test
    fun `BookImageResolver finds the unpadded scan of a page`(@TempDir dir: File) {
        File(dir, "61.jpg").writeBytes(byteArrayOf(1))
        assertEquals("61.jpg", BookImageResolver.scanFor(61, dir)?.name)
    }

    @Test
    fun `BookImageResolver finds the zero-padded scan of a page`(@TempDir dir: File) {
        File(dir, "007.png").writeBytes(byteArrayOf(1))
        assertEquals("007.png", BookImageResolver.scanFor(7, dir)?.name)
    }

    @Test
    fun `BookImageResolver finds a split scan of a page`(@TempDir dir: File) {
        File(dir, "83_1.jpg").writeBytes(byteArrayOf(1))
        assertEquals("83_1.jpg", BookImageResolver.scanFor(83, dir)?.name)
    }

    @Test
    fun `BookImageResolver returns null when no scan exists for a page`(@TempDir dir: File) {
        assertNull(BookImageResolver.scanFor(42, dir))
    }

    @Test
    fun `BookImageResolver resolves the exact scan of a split source file`(@TempDir dir: File) {
        File(dir, "83_1.jpg").writeBytes(byteArrayOf(1))
        assertEquals("83_1.jpg", BookImageResolver.scanForBase("83_1", dir)?.name)
    }

    @Test
    fun `BookImageResolver falls back to the page scan when the split has no scan`(@TempDir dir: File) {
        File(dir, "83.jpg").writeBytes(byteArrayOf(1))
        assertEquals("83.jpg", BookImageResolver.scanForBase("83_1", dir)?.name)
    }

    @Test
    fun `BookImageRewriter keeps a directive whose target file exists`(@TempDir dir: File) {
        File(dir, "real.png").writeBytes(byteArrayOf(1))
        val text = "Avant\nimage::real.png[Legende]\nApres"
        assertEquals(text, BookImageRewriter.repair(text, dir, File(dir, "61.jpg")))
    }

    @Test
    fun `BookImageRewriter substitutes a ghost block directive with the page scan preserving the form`(@TempDir dir: File) {
        File(dir, "157.jpg").writeBytes(byteArrayOf(1))
        val text = "== Section\nimage::qr-code.png[QR Code]\nSuite du texte"
        val repaired = BookImageRewriter.repair(text, dir, File(dir, "157.jpg"))
        assertTrue("image::157.jpg[QR Code]" in repaired, "the ghost must point at the page scan, got:\n$repaired")
        assertTrue("qr-code.png" !in repaired, "the ghost target must disappear, got:\n$repaired")
    }

    @Test
    fun `BookImageRewriter substitutes a ghost inline directive preserving the inline form`(@TempDir dir: File) {
        File(dir, "61.jpg").writeBytes(byteArrayOf(1))
        val text = "Le cerveau :\nimage:cerveau_gauche_vs_cerveau_droit.jpg[Brain]\nSuite"
        val repaired = BookImageRewriter.repair(text, dir, File(dir, "61.jpg"))
        assertTrue("image:61.jpg[Brain]" in repaired, "the inline ghost must point at the scan, got:\n$repaired")
        assertTrue("cerveau_gauche" !in repaired, "the ghost target must disappear, got:\n$repaired")
    }

    @Test
    fun `BookImageRewriter drops a ghost directive when the page has no scan`(@TempDir dir: File) {
        val text = "== Section\nimage::ghost.png[]\nSuite du texte"
        val repaired = BookImageRewriter.repair(text, dir, null)
        assertTrue("image::" !in repaired, "the ghost must be dropped, got:\n$repaired")
        assertTrue("Suite du texte" in repaired, "the surrounding text must survive, got:\n$repaired")
        assertTrue("ghost.png" !in repaired, "the ghost target must disappear, got:\n$repaired")
    }

    @Test
    fun `BookImageRewriter drops a ghost directive line carrying a trailing comment`(@TempDir dir: File) {
        val text = "== Section\nimage::triangle-gradient.png[] // schema du cycle\nSuite"
        val repaired = BookImageRewriter.repair(text, dir, null)
        assertTrue("image::" !in repaired, "the ghost must be dropped, got:\n$repaired")
        assertTrue("Suite" in repaired, "the next line must survive, got:\n$repaired")
    }

    @Test
    fun `BookImageRewriter is a no-op on content without any image directive`(@TempDir dir: File) {
        val text = "== Section\nAucune image ici.\nSuite"
        assertEquals(text, BookImageRewriter.repair(text, dir, File(dir, "61.jpg")))
    }

    @Test
    fun `BookImageRewriter targets lists distinct block and inline targets in order`(@TempDir dir: File) {
        val text = "image::a.png[]\ntexte\nimage:b.png[Legende]\nimage::a.png[Encore]"
        assertEquals(linkedSetOf("a.png", "b.png"), BookImageRewriter.targets(text))
    }

    @Test
    fun `BookImageRewriter targets is empty when no image directive is present`(@TempDir dir: File) {
        assertTrue(BookImageRewriter.targets("== Section\nAucune image.\n").isEmpty())
    }
}
