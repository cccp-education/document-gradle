package document.pdf

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue

/**
 * JSON report of the PDF validation (DOC-PDF-CHECK) — mirrors
 * [document.epub.EpubValidationReport]. Written by `validateDocumentPdf`
 * next to the other guards (`build/docs/document/pdf-validation-report.json`).
 */
data class PdfValidationReport(
    val tool: String = "pdfbox",
    val entries: List<PdfReportEntry>,
) {

    /**
     * One audit line: status VALID/INVALID + the finding list (absent when
     * valid — `@JsonInclude(NON_NULL)`, backward-compatible with the other
     * guards' reports).
     */
    data class PdfReportEntry(
        val status: String,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val issues: List<String>? = null,
    )

    fun toJson(): String =
        ObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(this)

    companion object {

        /** Builds the report from a validation result. */
        fun fromResult(result: PdfValidationResult): PdfValidationReport {
            val entry = when (result) {
                is PdfValidationResult.Valid ->
                    PdfReportEntry(status = "VALID")
                is PdfValidationResult.Invalid ->
                    PdfReportEntry(
                        status = "INVALID",
                        issues = result.issues.distinct().sorted(),
                    )
            }
            return PdfValidationReport(entries = listOf(entry))
        }

        /**
         * Parses a report JSON; returns null on blank/invalid input
         * (degraded silent — never crashes the collect path).
         */
        fun fromJson(json: String): PdfValidationReport? = try {
            if (json.isBlank()) {
                null
            } else {
                ObjectMapper()
                    .registerModule(KotlinModule.Builder().build())
                    .readValue(json)
            }
        } catch (@Suppress("unused") e: Exception) {
            null
        }
    }
}