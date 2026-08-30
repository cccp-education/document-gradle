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
 * Lints the *real* rendered FPA book (`livre-navigable/book.html`, produced by the
 * bookPipeline in S-217/218) for navigability : every internal `href="#id"` link
 * must resolve to a defined anchor, and the book must expose a table of contents
 * (`id="toc"`). This locks the "livre FPA SERVER" product artefact : no dead link
 * in the published HTML.
 *
 * The test self-skips (`assumeTrue`) when the corpus is absent, mirroring
 * [document.BookPipelineFpaFunctionalTest].
 */
class HtmlLinkLintFunctionalTest {

    companion object {
        private val FPA_BOOK_HTML = File(
            "/home/cheroliv/workspace/office/metiers/FPA",
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/livre-navigable/book.html",
        )
    }

    @Test
    fun `real FPA book html is navigable with no dead internal link`() {
        assumeTrue(FPA_BOOK_HTML.isFile) {
            "FPA book.html not found at ${FPA_BOOK_HTML.absolutePath}"
        }
        val html = FPA_BOOK_HTML.readText()
        assertTrue(HtmlLinkLinter.hasTableOfContents(html)) {
            "rendered FPA book must expose a table of contents (id=\"toc\")"
        }
        assertEquals(
            HtmlLinkLintResult.Valid,
            HtmlLinkLinter.validate(html),
            "rendered FPA book must have zero dead internal link",
        )
    }

    @Test
    fun `altered FPA book html with a dead link is reported Invalid`() {
        assumeTrue(FPA_BOOK_HTML.isFile) {
            "FPA book.html not found at ${FPA_BOOK_HTML.absolutePath}"
        }
        val html = FPA_BOOK_HTML.readText() +
            """<a href="#this-anchor-does-not-exist-in-the-fpa-book">broken</a>"""
        val result = HtmlLinkLinter.validate(html)
        assertTrue(result is HtmlLinkLintResult.Invalid)
        assertTrue(
            (result as HtmlLinkLintResult.Invalid)
                .deadLinks.contains("this-anchor-does-not-exist-in-the-fpa-book"),
        )
    }
}
