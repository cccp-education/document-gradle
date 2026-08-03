package document.translation.validation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

@JsonInclude(JsonInclude.Include.NON_NULL)
data class PlantUmlValidationReportEntry(
    val article: String,
    val blockIndex: Int,
    val status: String,
    val strategy: String? = null,
    val reason: String? = null,
)

data class PlantUmlValidationReport(
    val entries: List<PlantUmlValidationReportEntry>,
) {
    fun toJson(): String {
        val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
        return mapper.writeValueAsString(this)
    }

    companion object {
        fun fromResults(results: List<PlantUmlValidationResult.Invalid>): PlantUmlValidationReport {
            val entries = results.map { r ->
                PlantUmlValidationReportEntry(
                    article = r.articleTitle,
                    blockIndex = r.blockIndex,
                    status = "INVALID",
                    strategy = r.strategy,
                    reason = r.reason,
                )
            }
            return PlantUmlValidationReport(entries)
        }

        fun empty(): PlantUmlValidationReport = PlantUmlValidationReport(emptyList())
    }
}
