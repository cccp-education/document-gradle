package document.translation.validation

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableValidationReportTest {

    @Test
    fun `fromResults creates report with entries`() {
        val results = listOf(
            TableValidationResult.Invalid("Article 1", 0, "cols count mismatch"),
            TableValidationResult.Invalid("Article 2", 1, "delimiter in cell"),
        )

        val report = TableValidationReport.fromResults(results)

        assertEquals(2, report.entries.size)
        assertEquals("Article 1", report.entries[0].article)
        assertEquals(0, report.entries[0].tableIndex)
        assertEquals("INVALID", report.entries[0].status)
        assertEquals("cols count mismatch", report.entries[0].reason)
        assertEquals("Article 2", report.entries[1].article)
        assertEquals(1, report.entries[1].tableIndex)
        assertEquals("delimiter in cell", report.entries[1].reason)
    }

    @Test
    fun `empty creates report with no entries`() {
        val report = TableValidationReport.empty()

        assertTrue(report.entries.isEmpty())
    }

    @Test
    fun `toJson produces valid JSON`() {
        val results = listOf(
            TableValidationResult.Invalid("Article 1", 0, "cols count mismatch"),
        )
        val report = TableValidationReport.fromResults(results)

        val json = report.toJson()

        assertContains(json, "\"article\"")
        assertContains(json, "\"Article 1\"")
        assertContains(json, "\"tableIndex\"")
        assertContains(json, "\"status\"")
        assertContains(json, "\"INVALID\"")
        assertContains(json, "\"reason\"")
        assertContains(json, "\"cols count mismatch\"")
    }

    @Test
    fun `toJson for empty report produces valid JSON`() {
        val report = TableValidationReport.empty()

        val json = report.toJson()

        assertContains(json, "\"entries\"")
        assertContains(json, "[ ]")
    }

    @Test
    fun `fromResults with empty list returns empty report`() {
        val report = TableValidationReport.fromResults(emptyList())

        assertTrue(report.entries.isEmpty())
    }

    @Test
    fun `report entry with null reason omits field`() {
        val results = listOf(
            TableValidationResult.Invalid("Article", 0, ""),
        )
        val report = TableValidationReport.fromResults(results)

        val json = report.toJson()

        assertContains(json, "\"reason\"")
    }
}
