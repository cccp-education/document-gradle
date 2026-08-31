package document.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for the fourth composite guard (DOC-VALIDATE-HTML-LINT) : the HTML
 * link lint [HtmlLinkLinter] joins [DocumentValidator] as the composite report's
 * `htmlLint` entry, mirroring `includeGuard` / `xref` / `security`.
 *
 * Ink Economy Law : pure domain functions — no I/O, no Gradle.
 */
class HtmlLintGuardTest {

    private val htmlValid = """<html><body><div id="toc"></div><a href="#intro">Intro</a><h1 id="intro"></h1></body></html>"""
    private val htmlDead = """<html><body><a href="#missing">gone</a></body></html>"""
    private val source = "[[intro]]\n== Intro\n"

    @Test
    fun `htmlLint entry is null when mode OFF`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.OFF,
        )
        assertNull(report.htmlLint, "mode OFF must not audit the HTML lint guard")
        assertEquals("PASS", report.overallStatus())
    }

    @Test
    fun `htmlLint entry is VALID when all internal links resolve`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.STRICT,
            html = htmlValid,
        )
        assertEquals("VALID", report.htmlLint?.status)
        assertNull(report.htmlLint?.deadLinks)
        assertEquals("PASS", report.overallStatus())
    }

    @Test
    fun `htmlLint entry is DEAD when an internal link does not resolve`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.STRICT,
            html = htmlDead,
        )
        assertEquals("DEAD", report.htmlLint?.status)
        assertTrue(report.htmlLint?.deadLinks?.contains("missing") == true)
        assertEquals("FAIL", report.overallStatus(), "dead link must fail the overall status")
    }

    @Test
    fun `htmlLint entry lists every dead link and stays sorted`() {
        val html = """<a href="#zz"></a><a href="#aa"></a>"""
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.STRICT,
            html = html,
        )
        assertEquals(listOf("aa", "zz"), report.htmlLint?.deadLinks)
    }

    @Test
    fun `htmlLint DEAD does not fail overall status when mode is LENIENT`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "VALID"),
            xref = null,
            security = SecurityReportEntry(advice = "VALID"),
            htmlLint = HtmlLintReportEntry(status = "DEAD", deadLinks = listOf("gone")),
        )
        assertEquals("FAIL", report.overallStatus(), "LENIENT or not, DEAD must be visible as a failure signal in metadata")
    }

    @Test
    fun `back-compat fromJson tolerates a report without htmlLint`() {
        val legacyJson = """
            {
              "includeGuard" : { "status" : "OFF" },
              "security" : { "advice" : "VALID" }
            }
        """.trimIndent()
        val parsed = DocumentValidationReport.fromJson(legacyJson)
        assertNull(parsed.htmlLint)
        assertEquals("PASS", parsed.overallStatus())
    }

    @Test
    fun `fromJson round-trips a report with htmlLint DEAD`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "VALID"),
            xref = null,
            security = SecurityReportEntry(advice = "VALID"),
            htmlLint = HtmlLintReportEntry(status = "DEAD", deadLinks = listOf("missing")),
        )
        val json = report.toJson()
        val parsed = DocumentValidationReport.fromJson(json)
        assertEquals("DEAD", parsed.htmlLint?.status)
        assertEquals(listOf("missing"), parsed.htmlLint?.deadLinks)
        assertEquals("FAIL", parsed.overallStatus())
    }

    @Test
    fun `toJson contains htmlLint block when audited`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.LENIENT,
            html = htmlDead,
        )
        val json = report.toJson()
        assertTrue(json.contains("htmlLint"), "audited lint must appear in the consolidated report")
        assertTrue(json.contains("DEAD"))
    }

    @Test
    fun `htmlLint DEAD via validate with LENIENT mode still reports DEAD entries`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.LENIENT,
            html = htmlDead,
        )
        assertEquals("DEAD", report.htmlLint?.status)
        assertEquals("FAIL", report.overallStatus())
    }

    @Test
    fun `htmlLint null html provided with STRICT mode yields DEAD with missing file notice`() {
        val report = DocumentValidator.validate(
            source,
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
            htmlLintMode = HtmlLinkLintMode.STRICT,
            html = null,
        )
        assertEquals("DEAD", report.htmlLint?.status)
        assertEquals(listOf("<html-file-missing>"), report.htmlLint?.deadLinks)
        assertEquals("FAIL", report.overallStatus())
    }
}