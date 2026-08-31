package document.epub

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.File

/**
 * JSON report of the EPUB validation (DOC-EPUBCHECK) — mirrors
 * [document.xref.XrefValidationReport]. Written by `validateDocumentEpub`
 * next to the other guards (`build/docs/document/epub-validation-report.json`).
 */
data class EpubValidationReport(
    val tool: String = "epubcheck",
    val entries: List<EpubReportEntry>,
) {

    /**
     * One audit line: status VALID/INVALID + the epubcheck message list
     * (absent when valid — `@JsonInclude(NON_NULL)`, backward-compatible
     * with the other guards' reports).
     */
    data class EpubReportEntry(
        val status: String,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        val issues: List<String>? = null,
    )

    fun toJson(): String =
        ObjectMapper()
            .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .writerWithDefaultPrettyPrinter()
            .writeValueAsString(this)

    companion object {

        /** Builds the report from a validation result. */
        fun fromResult(result: EpubValidationResult): EpubValidationReport {
            val entry = when (result) {
                is EpubValidationResult.Valid ->
                    EpubReportEntry(status = "VALID")
                is EpubValidationResult.Invalid ->
                    EpubReportEntry(
                        status = "INVALID",
                        issues = result.issues.distinct().sorted(),
                    )
            }
            return EpubValidationReport(entries = listOf(entry))
        }

        /**
         * Parses a report JSON; returns null on blank/invalid input
         * (degraded silent — never crashes the collect path).
         */
        fun fromJson(json: String): EpubValidationReport? = try {
            if (json.isBlank()) {
                null
            } else {
                ObjectMapper()
                    .registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
                    .readValue(json, EpubValidationReport::class.java)
            }
        } catch (@Suppress("unused") e: Exception) {
            null
        }
    }
}