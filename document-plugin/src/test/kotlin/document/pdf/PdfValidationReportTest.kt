package document.pdf

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [PdfValidationReport] (US-1, EPIC DOC-PDF-CHECK) — pure
 * serialization contract (mirrors EpubValidationReportTest).
 */
class PdfValidationReportTest {

    @Test
    fun `fromResult Valid produces single VALID entry`() {
        val report = PdfValidationReport.fromResult(PdfValidationResult.Valid)

        assertThat(report.entries).hasSize(1)
        assertThat(report.entries[0].status).isEqualTo("VALID")
        assertThat(report.entries[0].issues).isNull()
    }

    @Test
    fun `fromResult Invalid produces entry with sorted deduplicated issues`() {
        val result = PdfValidationResult.Invalid(listOf("page 10: no extractable text", "page 2: no extractable text", "page 10: no extractable text"))
        val report = PdfValidationReport.fromResult(result)

        assertThat(report.entries[0].status).isEqualTo("INVALID")
        assertThat(report.entries[0].issues).containsExactly("page 10: no extractable text", "page 2: no extractable text")
    }

    @Test
    fun `report serializes to JSON with entry status and issues`() {
        val report = PdfValidationReport.fromResult(
            PdfValidationResult.Invalid(listOf("page 1: no extractable text")),
        )

        val json = report.toJson()

        assertThat(json).contains("\"tool\" : \"pdfbox\"")
        assertThat(json).contains("\"status\" : \"INVALID\"")
        assertThat(json).contains("page 1: no extractable text")
    }

    @Test
    fun `Valid report omits issues list (NON_NULL)`() {
        val json = PdfValidationReport.fromResult(PdfValidationResult.Valid).toJson()

        assertThat(json).doesNotContain("\"issues\"")
    }

    @Test
    fun `fromJson round-trips a VALID report`() {
        val json = PdfValidationReport.fromResult(PdfValidationResult.Valid).toJson()

        val parsed = PdfValidationReport.fromJson(json)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.entries[0].status).isEqualTo("VALID")
    }

    @Test
    fun `fromJson round-trips an INVALID report with issues`() {
        val report = PdfValidationReport.fromResult(
            PdfValidationResult.Invalid(listOf("page 2: no extractable text", "page 1: no extractable text")),
        )
        val json = report.toJson()

        val parsed = PdfValidationReport.fromJson(json)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.entries).hasSize(1)
        assertThat(parsed.entries[0].status).isEqualTo("INVALID")
        // fromResult sorts issues; the round-trip preserves the sorted order.
        assertThat(parsed.entries[0].issues).containsExactly("page 1: no extractable text", "page 2: no extractable text")
    }

    @Test
    fun `fromJson empty string returns null (degraded silent)`() {
        assertThat(PdfValidationReport.fromJson("")).isNull()
    }

    @Test
    fun `fromJson invalid json returns null (degraded silent)`() {
        assertThat(PdfValidationReport.fromJson("not json at all {")).isNull()
    }
}