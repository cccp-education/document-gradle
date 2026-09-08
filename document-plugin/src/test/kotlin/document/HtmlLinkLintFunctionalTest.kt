package document

import document.validation.HtmlLinkLinter
import document.validation.HtmlLinkLintResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Dogfooding test — EPIC DOC-HTML-LINT US-2.
 *
 * Lints the *real* rendered scanned-content book (`livre-navigable/book.html`, produced by the
 * bookPipeline in S-217/218) for navigability : every internal `href="#id"` link
 * must resolve to a defined anchor, and the book must expose a table of contents
 * (`id="toc"`). This locks the "livre content SERVER" product artefact : no dead link
 * in the published HTML.
 *
 * The test self-skips (`assumeTrue`) when the corpus is absent, mirroring
 * [document.BookPipelineContentFunctionalTest].
 */
class HtmlLinkLintFunctionalTest {

    companion object {
        private val CONTENT_BOOK_HTML = File(
            "/home/cheroliv/workspace/office/metiers/FPA",
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/livre-navigable/book.html",
        )
    }

    @Test
    fun `real scanned-content book html is navigable with no dead internal link`() {
        assumeTrue(CONTENT_BOOK_HTML.isFile) {
            "scanned-content book.html not found at ${CONTENT_BOOK_HTML.absolutePath}"
        }
        val html = CONTENT_BOOK_HTML.readText()
        assertTrue(HtmlLinkLinter.hasTableOfContents(html)) {
            "rendered scanned-content book must expose a table of contents (id=\"toc\")"
        }
        assertEquals(
            HtmlLinkLintResult.Valid,
            HtmlLinkLinter.validate(html),
            "rendered scanned-content book must have zero dead internal link",
        )
    }

    @Test
    fun `altered scanned-content book html with a dead link is reported Invalid`() {
        assumeTrue(CONTENT_BOOK_HTML.isFile) {
            "scanned-content book.html not found at ${CONTENT_BOOK_HTML.absolutePath}"
        }
        val html = CONTENT_BOOK_HTML.readText() +
            """<a href="#this-anchor-does-not-exist-in-the-content-book">broken</a>"""
        val result = HtmlLinkLinter.validate(html)
        assertTrue(result is HtmlLinkLintResult.Invalid)
        assertTrue(
            (result as HtmlLinkLintResult.Invalid)
                .deadLinks.contains("this-anchor-does-not-exist-in-the-content-book"),
        )
    }
}
