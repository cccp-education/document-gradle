package document.validation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Unit tests for the derived status of [DocumentValidationReport] (DOC-METADATA-VALIDATION).
 *
 * The overall status is a pure derivation of the three guard entries — no I/O,
 * no Gradle. It feeds metadata.json for runner-gradle N3 ingestion (US-2).
 */
class DocumentValidationReportStatusTest {

    @Test
    fun `overall status PASS when includeGuard valid, xref off, security valid`() {
        val report = DocumentValidator.validate(
            "[[intro]]\n== Intro\n",
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
        )
        assertEquals("PASS", report.overallStatus())
    }

    @Test
    fun `overall status FAIL when includeGuard INVALID`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "INVALID", reason = "traversal"),
            xref = null,
            security = SecurityReportEntry(advice = "VALID"),
        )
        assertEquals("FAIL", report.overallStatus())
    }

    @Test
    fun `overall status FAIL when xref MISSING`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "VALID"),
            xref = XrefReportEntry(status = "MISSING", missing = listOf("gone")),
            security = SecurityReportEntry(advice = "VALID"),
        )
        assertEquals("FAIL", report.overallStatus())
    }

    @Test
    fun `overall status FAIL when security REJECT`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "VALID"),
            xref = null,
            security = SecurityReportEntry(advice = "REJECT", reason = "unsafe"),
        )
        assertEquals("FAIL", report.overallStatus())
    }

    @Test
    fun `overall status PASS when security WARN (lenient asymmetry)`() {
        val report = DocumentValidationReport(
            includeGuard = IncludeGuardReportEntry(status = "VALID"),
            xref = null,
            security = SecurityReportEntry(advice = "WARN", reason = "unsafe"),
        )
        assertEquals("PASS", report.overallStatus())
    }

    @Test
    fun `fromJson round-trips a serialized report`() {
        val report = DocumentValidator.validate(
            "[[a]]\n== A\n\nSee <<a>>.\n",
            java.io.File("src/test/resources/validation"),
            document.security.IncludeGuardMode.STRICT,
            document.xref.XrefValidationMode.STRICT,
            org.asciidoctor.SafeMode.UNSAFE,
        )
        val json = report.toJson()
        val parsed = DocumentValidationReport.fromJson(json)
        assertEquals(report.includeGuard.status, parsed.includeGuard.status)
        assertEquals(report.xref?.status, parsed.xref?.status)
        assertEquals(report.security.advice, parsed.security.advice)
        assertEquals(report.overallStatus(), parsed.overallStatus())
    }

    @Test
    fun `fromJson tolerates missing nullable xref`() {
        val report = DocumentValidator.validate(
            "== A\n",
            java.io.File("."),
            document.security.IncludeGuardMode.OFF,
            document.xref.XrefValidationMode.OFF,
            org.asciidoctor.SafeMode.UNSAFE,
        )
        assertNull(report.xref)
        val parsed = DocumentValidationReport.fromJson(report.toJson())
        assertNull(parsed.xref)
        assertNotNull(parsed.includeGuard)
        assertEquals("PASS", parsed.overallStatus())
    }
}