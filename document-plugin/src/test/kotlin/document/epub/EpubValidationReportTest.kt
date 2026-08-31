package document.epub

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.annotation.JsonInclude
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Unit tests for [EpubValidationReport] (US-1, EPIC DOC-EPUBCHECK) — pure
 * serialization contract (mirrors XrefValidationReportTest).
 */
class EpubValidationReportTest {

    @Test
    fun `fromResult Valid produces single VALID entry`() {
        val report = EpubValidationReport.fromResult(EpubValidationResult.Valid)

        assertThat(report.entries).hasSize(1)
        assertThat(report.entries[0].status).isEqualTo("VALID")
        assertThat(report.entries[0].issues).isNull()
    }

    @Test
    fun `fromResult Invalid produces entry with sorted deduplicated issues`() {
        val result = EpubValidationResult.Invalid(listOf("B-2", "A-1", "B-2"))
        val report = EpubValidationReport.fromResult(result)

        assertThat(report.entries[0].status).isEqualTo("INVALID")
        assertThat(report.entries[0].issues).containsExactly("A-1", "B-2")
    }

    @Test
    fun `report serializes to JSON with entry status and issues`() {
        val report = EpubValidationReport.fromResult(
            EpubValidationResult.Invalid(listOf("OPF-001: missing nav")),
        )

        val json = report.toJson()

        assertThat(json).contains("\"tool\" : \"epubcheck\"")
        assertThat(json).contains("\"status\" : \"INVALID\"")
        assertThat(json).contains("OPF-001: missing nav")
    }

    @Test
    fun `Valid report omits issues list (NON_NULL)`() {
        val json = EpubValidationReport.fromResult(EpubValidationResult.Valid).toJson()

        assertThat(json).doesNotContain("\"issues\"")
    }

    @Test
    fun `fromJson round-trips a VALID report`() {
        val json = EpubValidationReport.fromResult(EpubValidationResult.Valid).toJson()

        val parsed = EpubValidationReport.fromJson(json)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.entries[0].status).isEqualTo("VALID")
    }

    @Test
    fun `fromJson round-trips an INVALID report with issues`() {
        val report = EpubValidationReport.fromResult(
            EpubValidationResult.Invalid(listOf("OPF-001", "CSS-009")),
        )
        val json = report.toJson()

        val parsed = EpubValidationReport.fromJson(json)

        assertThat(parsed).isNotNull
        assertThat(parsed!!.entries).hasSize(1)
        assertThat(parsed.entries[0].status).isEqualTo("INVALID")
        // fromResult sorts issues; the round-trip preserves the sorted order.
        assertThat(parsed.entries[0].issues).containsExactly("CSS-009", "OPF-001")
    }

    @Test
    fun `fromJson empty string returns null (degraded silent)`() {
        assertThat(EpubValidationReport.fromJson("")).isNull()
    }

    @Test
    fun `fromJson invalid json returns null (degraded silent)`() {
        assertThat(EpubValidationReport.fromJson("not json at all {")).isNull()
    }
}