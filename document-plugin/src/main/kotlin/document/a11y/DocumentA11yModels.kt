package document.a11y

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule

/** DOC-A11Y — stable rule identifiers. */
object DocumentA11yRules {
    const val HTML_LANG_MISSING = "html-lang-missing"
    const val HTML_IMAGE_ALT_MISSING = "html-image-alt-missing"
    const val HTML_TABLE_HEADER_MISSING = "html-table-header-missing"
    const val HTML_H1_MISSING = "html-h1-missing"
    const val EPUB_LANG_MISSING = "epub-lang-missing"
    const val EPUB_IMAGE_ALT_MISSING = "epub-image-alt-missing"
    const val EPUB_TABLE_HEADER_MISSING = "epub-table-header-missing"
    const val EPUB_ACCESSIBILITY_METADATA_MISSING = "epub-accessibility-metadata-missing"
    const val EPUB_UNREADABLE = "epub-unreadable"
    const val PDF_NOT_TAGGED = "pdf-not-tagged"
}

/** DOC-A11Y — finding severities, aligned with the academy consumer. */
object DocumentA11ySeverity {
    const val ERROR = "ERROR"
    const val WARNING = "WARNING"
    const val INFO = "INFO"
}

/**
 * DOC-A11Y — one finding of the producer report.
 *
 * The field names are the **consumer contract**: academy's
 * `ProducerReportInspector` reads `rule`, `severity`, `location`, `message`.
 */
data class DocumentA11yFinding(
    val rule: String,
    val severity: String,
    val location: String,
    val message: String,
)

/** DOC-A11Y — small factory shared by the inspectors. */
internal fun a11yFinding(
    rule: String,
    severity: String,
    location: String,
    message: String,
): DocumentA11yFinding = DocumentA11yFinding(rule = rule, severity = severity, location = location, message = message)

/** DOC-A11Y — the findings and declared limits of one deliverable kind. */
data class DeliverableA11yReport(
    val kind: String,
    val findings: List<DocumentA11yFinding> = emptyList(),
    val limits: List<String> = emptyList(),
)

/**
 * DOC-A11Y — the accessibility report written next to the generated artifacts.
 *
 * Contract: `a11y-report.json`, `deliverables[].kind` (HTML/EPUB/PDF),
 * `findings[]` and `limits[]`. It is consumed by academy's
 * `ProducerReportInspector` (`GBL-011-3`), so the JSON shape is a public
 * interface, not an internal detail.
 */
data class DocumentA11yReport(
    val version: Int = 1,
    val deliverables: List<DeliverableA11yReport> = emptyList(),
) {
    fun toJson(): String = mapper().writerWithDefaultPrettyPrinter().writeValueAsString(this)

    companion object {
        private fun mapper(): ObjectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

        fun fromJson(text: String): DocumentA11yReport = mapper().readValue(text, DocumentA11yReport::class.java)
    }
}
