package document

import document.epub.EpubValidationResult
import document.epub.LibEpubCheckAdapter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Dogfooding test — 5th guard (DOC-EPUBCHECK) against the real scanned-content book EPUB.
 *
 * Audits `livre-navigable/book.epub` (produced by the bookPipeline in S-217/218,
 * mtime 2026-08-31) with the W3C epubcheck library — the exact same adapter the
 * `validateDocumentEpub` task wires. Read-only ink-economy audit of the existing
 * artefact: no regeneration, no JRuby conversion (mirrors
 * [[HtmlLinkLintFunctionalTest] which reads the HTML artefact in place).
 *
 * This locks the "livre content SERVER" product: the shipped EPUB must pass W3C
 * validation, not just open. The test self-skips (`assumeTrue`) when the corpus
 * is absent.
 */
class EpubCheckContentFunctionalTest {

    companion object {
        private val CONTENT_BOOK_EPUB = File(
            "/home/cheroliv/workspace/office/metiers/FPA",
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/livre-navigable/book.epub",
        )
    }

    @Test
    fun `real scanned-content book epub passes epubcheck`() {
        assumeTrue(CONTENT_BOOK_EPUB.isFile) {
            "scanned-content book.epub not found at ${CONTENT_BOOK_EPUB.absolutePath}"
        }
        val result = LibEpubCheckAdapter().validate(CONTENT_BOOK_EPUB)
        assertEquals(
            EpubValidationResult.Valid,
            result,
            "shipped scanned-content EPUB must pass W3C epubcheck; issues=${(result as? EpubValidationResult.Invalid)?.issues}",
        )
    }

    @Test
    fun `altered scanned-content book epub copy is reported Invalid - negative proof`() {
        assumeTrue(CONTENT_BOOK_EPUB.isFile) {
            "scanned-content book.epub not found at ${CONTENT_BOOK_EPUB.absolutePath}"
        }
        // Structural corruption on a byte copy (never the source artefact — Rule 7
        // read-only): rebuild the zip WITHOUT the mandatory uncompressed `mimetype`
        // entry. The OCF container spec requires it as the very first stored entry;
        // epubcheck therefore reports a container violation.
        val copy = File.createTempFile("content-book-corrupted", ".epub")
        copy.deleteOnExit()
        val entries = ZipFile(CONTENT_BOOK_EPUB).use { zip ->
            zip.entries().asSequence().filter { it.name != "mimetype" }.map { entry ->
                zip.getInputStream(entry).use { input -> entry.name to input.readBytes() }
            }.toList()
        }
        ZipOutputStream(copy.outputStream().buffered()).use { out ->
            entries.forEach { (name, data) ->
                out.putNextEntry(ZipEntry(name))
                out.write(data)
                out.closeEntry()
            }
        }
        val result = LibEpubCheckAdapter().validate(copy)
        assertTrue(result is EpubValidationResult.Invalid) {
            "a corrupted EPUB copy must be reported Invalid, got Valid"
        }
        assertTrue(
            (result as EpubValidationResult.Invalid).issues.isNotEmpty(),
            "Invalid result must carry the epubcheck issue list",
        )
    }
}