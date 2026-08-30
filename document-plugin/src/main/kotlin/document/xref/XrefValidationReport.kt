package document.xref

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/**
 * JSON report of a cross-reference validation, parallel to
 * [document.translation.validation.TableValidationReport].
 *
 * Ink Economy Law: the report is a pure serialisable value — the validator
 * produces it, a task persists it; no side effect inside the report itself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class XrefValidationReportEntry(
    val ref: String,
    val status: String,
    val reason: String? = null,
)

data class XrefValidationReport(
    val entries: List<XrefValidationReportEntry>,
) {
    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }

    companion object {
        fun fromResult(result: XrefValidationResult): XrefValidationReport {
            val entries = when (result) {
                is XrefValidationResult.Valid ->
                    listOf(XrefValidationReportEntry(ref = "*", status = "VALID"))
                is XrefValidationResult.Invalid ->
                    result.missing.map {
                        XrefValidationReportEntry(
                            ref = it,
                            status = "MISSING",
                            reason = "cross-reference '$it' has no matching anchor",
                        )
                    }
            }
            return XrefValidationReport(entries)
        }

        fun empty(): XrefValidationReport = XrefValidationReport(emptyList())
    }
}
