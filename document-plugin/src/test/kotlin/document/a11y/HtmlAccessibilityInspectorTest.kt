package document.a11y

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DOC-A11Y — the producer-level HTML checks.
 *
 * `lang` and `alt` are errors (a screen reader cannot announce the page or the
 * image); a missing table header or `h1` is a warning. `alt=""` is accepted.
 */
class HtmlAccessibilityInspectorTest {
    private fun inspect(html: String) = HtmlAccessibilityInspector.inspect(html, "document.html")

    @Test
    fun `a well-formed document has no finding`() {
        val html =
            """
            <html lang="fr">
              <body>
                <h1>Cours</h1>
                <img src="scan.png" alt="page 12 du livre"/>
                <table><tr><th>Notion</th></tr><tr><td>Bloom</td></tr></table>
              </body>
            </html>
            """.trimIndent()

        assertTrue(inspect(html).isEmpty())
    }

    @Test
    fun `a missing lang is an error`() {
        val findings = inspect("<html><body><h1>T</h1></body></html>")

        assertTrue(findings.any { it.rule == DocumentA11yRules.HTML_LANG_MISSING && it.severity == DocumentA11ySeverity.ERROR })
    }

    @Test
    fun `an image without alt is an error`() {
        val findings = inspect("<html lang=\"fr\"><body><h1>T</h1><img src=\"scan.png\"></body></html>")

        assertTrue(findings.any { it.rule == DocumentA11yRules.HTML_IMAGE_ALT_MISSING })
    }

    @Test
    fun `an empty alt is accepted as decorative`() {
        val findings = inspect("<html lang=\"fr\"><body><h1>T</h1><img src=\"rule.png\" alt=\"\"></body></html>")

        assertTrue(findings.none { it.rule == DocumentA11yRules.HTML_IMAGE_ALT_MISSING })
    }

    @Test
    fun `a table without th is a warning`() {
        val findings = inspect("<html lang=\"fr\"><body><h1>T</h1><table><tr><td>x</td></tr></table></body></html>")

        assertTrue(findings.any { it.rule == DocumentA11yRules.HTML_TABLE_HEADER_MISSING && it.severity == DocumentA11ySeverity.WARNING })
    }

    @Test
    fun `the report location is the file name`() {
        val findings = HtmlAccessibilityInspector.inspect("<html><body></body></html>", "book.html")

        assertEquals("book.html", findings.first().location)
    }
}
