package document.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * TDD RED→GREEN — EPIC DOC-HTML-LINT US-1.
 * Pure DDD unit tests for [HtmlLinkLinter], mirroring [document.xref.XrefValidatorTest].
 */
class HtmlLinkLinterTest {

    @Test
    fun `extractAnchors collects double-quoted id attribute`() {
        val html = """<h2 id="chapter-1">Title</h2><p id="intro">Hi</p>"""
        assertEquals(setOf("chapter-1", "intro"), HtmlLinkLinter.extractAnchors(html))
    }

    @Test
    fun `extractAnchors collects single-quoted id attribute`() {
        val html = """<h2 id='chapter-2'>Title</h2>"""
        assertTrue(HtmlLinkLinter.extractAnchors(html).contains("chapter-2"))
    }

    @Test
    fun `extractAnchors collects legacy name attribute as anchor`() {
        val html = """<a name="legacy-anchor"></a>"""
        assertTrue(HtmlLinkLinter.extractAnchors(html).contains("legacy-anchor"))
    }

    @Test
    fun `extractAnchors ignores non-id attributes like aria-id`() {
        val html = """<div aria-id="not-an-anchor" id="real">x</div>"""
        assertEquals(setOf("real"), HtmlLinkLinter.extractAnchors(html))
    }

    @Test
    fun `extractInternalLinks collects double-quoted fragment href`() {
        val html = """<a href="#chapter-1">link</a>"""
        assertEquals(listOf("chapter-1"), HtmlLinkLinter.extractInternalLinks(html))
    }

    @Test
    fun `extractInternalLinks collects single-quoted fragment href`() {
        val html = """<a href='#intro'>link</a>"""
        assertEquals(listOf("intro"), HtmlLinkLinter.extractInternalLinks(html))
    }

    @Test
    fun `extractInternalLinks ignores external http links`() {
        val html = """<a href="https://example.com">ext</a><a href="#local">loc</a>"""
        assertEquals(listOf("local"), HtmlLinkLinter.extractInternalLinks(html))
    }

    @Test
    fun `extractInternalLinks ignores empty fragment`() {
        val html = """<a href="#">top</a><a href="#real">r</a>"""
        assertEquals(listOf("real"), HtmlLinkLinter.extractInternalLinks(html))
    }

    @Test
    fun `validate returns Valid when every internal link resolves`() {
        val html = """<h2 id="a">A</h2><a href="#a">to a</a>"""
        assertEquals(HtmlLinkLintResult.Valid, HtmlLinkLinter.validate(html))
    }

    @Test
    fun `validate returns Invalid with single dead link`() {
        val html = """<a href="#missing">dead</a>"""
        val result = HtmlLinkLinter.validate(html)
        assertTrue(result is HtmlLinkLintResult.Invalid)
        assertEquals(listOf("missing"), (result as HtmlLinkLintResult.Invalid).deadLinks)
    }

    @Test
    fun `validate returns Invalid with sorted deduplicated dead links`() {
        val html = """<a href="#z">z</a><a href="#a">a</a><a href="#z">z</a>"""
        val result = HtmlLinkLinter.validate(html)
        assertTrue(result is HtmlLinkLintResult.Invalid)
        assertEquals(listOf("a", "z"), (result as HtmlLinkLintResult.Invalid).deadLinks)
    }

    @Test
    fun `report serialises table of contents and dead links`() {
        val html = """<div id="toc"><ul></ul></div><a href="#missing">dead</a>"""
        val result = HtmlLinkLinter.validate(html)
        val report = HtmlLinkLintReport.fromResult(result, HtmlLinkLinter.hasTableOfContents(html))
        assertTrue(report.tocPresent)
        assertTrue(report.entries.any { it.status == "DEAD" && it.ref == "missing" })
        val json = report.toJson()
        assertTrue(json.contains("\"tocPresent\" : true") || json.contains("\"tocPresent\":true"))
        assertTrue(json.contains("\"DEAD\""))
        assertTrue(json.contains("\"missing\""))
    }
}
