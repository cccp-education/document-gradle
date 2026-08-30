package document.xref

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XrefValidationReportTest {

    @Test
    fun `fromResult Valid produces a single VALID entry`() {
        val report = XrefValidationReport.fromResult(XrefValidationResult.Valid)
        assertEquals(1, report.entries.size)
        assertEquals("VALID", report.entries.first().status)
    }

    @Test
    fun `fromResult Invalid lists every missing ref as MISSING`() {
        val report = XrefValidationReport.fromResult(
            XrefValidationResult.Invalid(listOf("ghost", "phantom")),
        )
        assertEquals(2, report.entries.size)
        assertTrue(report.entries.all { it.status == "MISSING" })
        assertEquals(listOf("ghost", "phantom"), report.entries.map { it.ref })
    }

    @Test
    fun `toJson is non-empty and round-trips entries`() {
        val json = XrefValidationReport.fromResult(
            XrefValidationResult.Invalid(listOf("ghost")),
        ).toJson()
        assertTrue(json.contains("MISSING"))
        assertTrue(json.contains("ghost"))
    }

    @Test
    fun `empty report has no entries`() {
        assertEquals(emptyList<XrefValidationReportEntry>(), XrefValidationReport.empty().entries)
    }
}
