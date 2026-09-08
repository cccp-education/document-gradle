package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Integration dogfooding — generates a *navigable* HTML/PDF/EPUB of the real
 * scanned-content book using the structured assembly (DOC-BOOK-DOMAIN) +
 * AsciidoctorJ converters (BOOK-4).
 *
 * The scanned content corpus (OCR-ed AsciiDoc pages in `office/metiers/FPA/.../scans/`
 * and the scan-aligned `toc.adoc`) is consumed read-only (Rule 7). The test
 * self-skips when the corpus is absent.
 *
 * By default the artifacts are written under the test working directory. Set
 * the `content.book.publish` system property to also copy the generated
 * HTML/PDF/EPUB into `office/metiers/FPA/.../livre-navigable/` (the real
 * deliverable for the content consumer).
 */
@Tag("integration")
class ContentBookArtifactGenerationTest {

    companion object {
        private val CONTENT_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val CONTENT_TOC = File(CONTENT_DIR, "toc.adoc")
        private val CONTENT_SCANS = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
        private val PUBLISH = System.getProperty("content.book.publish") != null
    }

    @TempDir
    lateinit var workDir: File

    @Test
    fun `generate a navigable HTML, PDF and EPUB of the real scanned-content book`() {
        assumeTrue(CONTENT_TOC.isFile) { "content TOC not found at ${CONTENT_TOC.absolutePath}" }
        assumeTrue(CONTENT_SCANS.isDirectory) { "content scans not found at ${CONTENT_SCANS.absolutePath}" }

        // --- structured assembly (DOC-BOOK-DOMAIN)
        val sections = BookTocParser.parse(CONTENT_TOC)
        assumeTrue(sections.isNotEmpty()) { "content TOC parsed to no sections" }
        val tree = BookTreeBuilder.fromSections(sections)
        val resolver: (BookSection) -> String = { section ->
            val page = File(CONTENT_SCANS, section.pdfFile)
            if (page.isFile) page.readText().trim() else ""
        }
        val assembled = BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(),
            title = "Devenir Formateur Professionnel d'Adultes - Tome II",
            author = "CCCP Education",
            resolveContent = resolver,
        )
        assertTrue(assembled.content.isNotBlank(), "assembled book must not be blank")
        assertTrue(assembled.content.contains("[[1.0.0]]"), "cross-reference anchor must be emitted")

        val bookAdoc = workDir.resolve("book.adoc").apply { writeText(assembled.content) }
        val html = workDir.resolve("book.html")
        val pdf = workDir.resolve("book.pdf")
        val epub = workDir.resolve("book.epub")

        // --- conversion (DOC-3 / DOC-4 / DOC-5)
        // convertToHtml returns the HTML as a String (in-memory) — we persist it.
        val htmlContent = DocumentConverter.convertToHtml(DocumentSource(bookAdoc), DocumentTheme())
        html.writeText(htmlContent)
        DocumentConverter.convertToPdf(DocumentSource(bookAdoc), pdf, DocumentTheme())
        DocumentConverter.convertToEpub(DocumentSource(bookAdoc), epub, DocumentTheme())

        assertTrue(html.exists() && html.length() > 0, "HTML must be non-empty")
        assertTrue(pdf.exists() && pdf.length() > 0, "PDF must be non-empty")
        assertTrue(epub.exists() && epub.length() > 0, "EPUB must be non-empty")

        assertTrue(htmlContent.contains("Devenir Formateur"), "HTML must contain the book title")
        assertTrue(htmlContent.contains(" id=\""), "HTML must carry navigable anchors")

        assertTrue(
            pdf.readText(Charsets.ISO_8859_1).startsWith("%PDF"),
            "PDF must be a valid PDF document",
        )
        assertTrue(
            epub.readBytes().copyOf(4).contentEquals("PK\u0003\u0004".toByteArray()),
            "EPUB must be a zip archive",
        )

        if (PUBLISH) {
            val outDir = File(
                CONTENT_DIR,
                "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/livre-navigable",
            ).apply { mkdirs() }
            bookAdoc.copyTo(outDir.resolve("book.adoc"), overwrite = true)
            html.copyTo(outDir.resolve("book.html"), overwrite = true)
            pdf.copyTo(outDir.resolve("book.pdf"), overwrite = true)
            epub.copyTo(outDir.resolve("book.epub"), overwrite = true)
            println("BOOK-4 — published navigable book artifacts to ${outDir.absolutePath}")
        }
    }
}
