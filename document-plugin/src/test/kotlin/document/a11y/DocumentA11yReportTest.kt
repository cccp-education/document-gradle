package document.a11y

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DOC-A11Y — the producer report is the **consumer contract** read by academy's
 * `ProducerReportInspector`: `deliverables[].kind`, `findings[].rule/severity/
 * location/message`, `deliverables[].limits`.
 */
class DocumentA11yReportTest {
    private val report =
        DocumentA11yReport(
            deliverables =
                listOf(
                    DeliverableA11yReport(
                        kind = "HTML",
                        findings =
                            listOf(
                                DocumentA11yFinding(
                                    rule = DocumentA11yRules.HTML_LANG_MISSING,
                                    severity = DocumentA11ySeverity.ERROR,
                                    location = "document.html",
                                    message = "lang",
                                ),
                            ),
                        limits = listOf("PDF is not PDF/UA"),
                    ),
                ),
        )

    @Test
    fun `the json carries the consumer contract field names`() {
        val json = report.toJson()

        assertTrue(json.contains("\"deliverables\""))
        assertTrue(json.contains("\"kind\""))
        assertTrue(json.contains("\"rule\""))
        assertTrue(json.contains("\"severity\""))
        assertTrue(json.contains("\"location\""))
        assertTrue(json.contains("\"limits\""))
    }

    @Test
    fun `the report round-trips`() {
        assertEquals(report, DocumentA11yReport.fromJson(report.toJson()))
    }
}
