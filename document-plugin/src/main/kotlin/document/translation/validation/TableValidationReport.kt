package document.translation.validation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TableValidationReportEntry(
    val article: String,
    val tableIndex: Int,
    val status: String,
    val reason: String? = null,
)

data class TableValidationReport(
    val entries: List<TableValidationReportEntry>,
) {
    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }

    companion object {
        fun fromResults(results: List<TableValidationResult.Invalid>): TableValidationReport {
            val entries = results.map { r ->
                TableValidationReportEntry(
                    article = r.articleTitle,
                    tableIndex = r.tableIndex,
                    status = "INVALID",
                    reason = r.reason,
                )
            }
            return TableValidationReport(entries)
        }

        fun empty(): TableValidationReport = TableValidationReport(emptyList())
    }
}
