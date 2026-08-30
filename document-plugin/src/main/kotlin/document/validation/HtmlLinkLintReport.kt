package document.validation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

/**
 * JSON report of an HTML link lint, parallel to [document.xref.XrefValidationReport].
 *
 * Ink Economy Law: the report is a pure serialisable value — the linter produces
 * it, a task persists it; no side effect inside the report itself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HtmlLinkLintReportEntry(
    val ref: String,
    val status: String,
    val reason: String? = null,
)

data class HtmlLinkLintReport(
    val tocPresent: Boolean,
    val entries: List<HtmlLinkLintReportEntry>,
) {
    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }

    companion object {
        fun fromResult(result: HtmlLinkLintResult, tocPresent: Boolean): HtmlLinkLintReport {
            val entries = when (result) {
                is HtmlLinkLintResult.Valid ->
                    listOf(HtmlLinkLintReportEntry(ref = "*", status = "VALID"))
                is HtmlLinkLintResult.Invalid ->
                    result.deadLinks.map {
                        HtmlLinkLintReportEntry(
                            ref = it,
                            status = "DEAD",
                            reason = "internal link '#$it' has no matching anchor (id/name)",
                        )
                    }
            }
            return HtmlLinkLintReport(tocPresent = tocPresent, entries = entries)
        }

        fun empty(): HtmlLinkLintReport = HtmlLinkLintReport(tocPresent = false, entries = emptyList())
    }
}
