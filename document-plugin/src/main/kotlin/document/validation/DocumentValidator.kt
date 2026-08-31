package document.validation

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import document.security.DocumentSecurityPolicy
import document.security.IncludeGuardMode
import document.security.IncludeValidationResult
import document.security.IncludePathValidator
import document.security.SecurityAdvice
import document.xref.XrefValidationMode
import document.xref.XrefValidationResult
import document.xref.XrefValidator
import org.asciidoctor.SafeMode
import java.io.File

/**
 * Pure DDD orchestrator composing the document conversion guards (DOC-VALIDATE-COMPOSITE) :
 * - include-path guard (DOC-CR4, [document.security.IncludePathValidator]) ;
 * - cross-reference validation (DOC-XREF-VALIDATE, [document.xref.XrefValidator]) ;
 * - conversion security policy advice (DOC-CR5, [document.security.DocumentSecurityPolicy]) ;
 * - HTML link lint of the rendered output (DOC-HTML-LINT, [HtmlLinkLinter] — DOC-VALIDATE-HTML-LINT,
 *   fourth guard, operates on the rendered HTML rather than the AsciiDoc source).
 *
 * Ink Economy Law : [validate] is a deterministic pure function of the source text, the HTML
 * text and the guard modes — no file is read, no Gradle dependency, fully unit-testable. The caller
 * (the task) performs I/O (read source, read HTML, write report). The orchestrator only classifies.
 */
object DocumentValidator {

    fun validate(
        text: String,
        baseDir: File,
        includeGuardMode: IncludeGuardMode,
        xrefMode: XrefValidationMode,
        safeMode: SafeMode,
        htmlLintMode: HtmlLinkLintMode = HtmlLinkLintMode.OFF,
        html: String? = null,
    ): DocumentValidationReport {
        val includeEntry = if (includeGuardMode == IncludeGuardMode.OFF) {
            IncludeGuardReportEntry(status = "OFF")
        } else {
            when (val result = IncludePathValidator.validate(text, baseDir)) {
                is IncludeValidationResult.Valid ->
                    IncludeGuardReportEntry(status = "VALID")
                is IncludeValidationResult.Invalid ->
                    IncludeGuardReportEntry(
                        status = "INVALID",
                        reason = result.reason,
                        offendingTarget = result.offendingTarget,
                        line = result.line,
                    )
            }
        }

        val xrefEntry = if (xrefMode == XrefValidationMode.OFF) {
            null
        } else {
            when (val result = XrefValidator.validate(text)) {
                is XrefValidationResult.Valid -> XrefReportEntry(status = "VALID")
                is XrefValidationResult.Invalid -> XrefReportEntry(status = "MISSING", missing = result.missing)
            }
        }

        val securityEntry = when (val advice = DocumentSecurityPolicy.advise(safeMode, includeGuardMode)) {
            is SecurityAdvice.Valid -> SecurityReportEntry(advice = "VALID")
            is SecurityAdvice.Warn -> SecurityReportEntry(advice = "WARN", reason = advice.reason)
            is SecurityAdvice.Reject -> SecurityReportEntry(advice = "REJECT", reason = advice.reason)
        }

        // Fourth guard (DOC-VALIDATE-HTML-LINT) — audits the *rendered* HTML when a mode
        // other than OFF is requested. A missing HTML file (html == null) under a non-OFF
        // mode is a DEAD finding pointing at the conversion prerequisite, not a silent skip.
        val htmlLintEntry = when {
            htmlLintMode == HtmlLinkLintMode.OFF -> null
            html == null ->
                HtmlLintReportEntry(
                    status = "DEAD",
                    deadLinks = listOf("<html-file-missing>"),
                    reason = "htmlLintMode=${htmlLintMode.name} requires the rendered HTML of convertDocumentToHtml",
                )
            else ->
                when (val result = HtmlLinkLinter.validate(html)) {
                    is HtmlLinkLintResult.Valid -> HtmlLintReportEntry(status = "VALID")
                    is HtmlLinkLintResult.Invalid ->
                        HtmlLintReportEntry(status = "DEAD", deadLinks = result.deadLinks)
                }
        }

        return DocumentValidationReport(
            includeGuard = includeEntry,
            xref = xrefEntry,
            security = securityEntry,
            htmlLint = htmlLintEntry,
        )
    }
}

/**
 * Serializable aggregate report of the composite document validation (DOC-VALIDATE-COMPOSITE).
 *
 * Ink Economy Law : the report is a pure value object — the orchestrator produces it, the task
 * persists it; no side effect inside the report itself.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class IncludeGuardReportEntry(
    val status: String,
    val reason: String? = null,
    val offendingTarget: String? = null,
    val line: Int? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class XrefReportEntry(
    val status: String,
    val missing: List<String> = emptyList(),
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class SecurityReportEntry(
    val advice: String,
    val reason: String? = null,
)

/**
 * Fourth composite guard entry (DOC-VALIDATE-HTML-LINT) — mirrors the shape of
 * [XrefReportEntry] : status is `VALID` (every internal link resolves) or `DEAD`
 * (dead internal links found, listed in [deadLinks]). `null` when the guard mode
 * is OFF (not audited). `NON_NULL` on [deadLinks] keeps backward-compatible JSON
 * for VALID entries.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class HtmlLintReportEntry(
    val status: String,
    val deadLinks: List<String>? = null,
    val reason: String? = null,
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class DocumentValidationReport(
    val includeGuard: IncludeGuardReportEntry,
    val xref: XrefReportEntry?,
    val security: SecurityReportEntry,
    val htmlLint: HtmlLintReportEntry? = null,
) {
    fun toJson(): String =
        ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this)

    /**
     * Derived overall status of the composite validation (DOC-METADATA-VALIDATION) :
     * - FAIL — any guard failed (includeGuard INVALID, xref MISSING, security REJECT) ;
     * - PASS — otherwise (WARN is visibility, not failure : LENIENT asymmetry is
     *   reported in metadata but must not block the N3 ingest).
     *
     * Pure derivation — no I/O, no Gradle. Consumed by [document.DocumentMetadata]
     * to carry the validation status into metadata.json (runner-gradle N3).
     */
    fun overallStatus(): String =
        if (includeGuard.status == "INVALID" ||
            xref?.status == "MISSING" ||
            security.advice == "REJECT" ||
            htmlLint?.status == "DEAD"
        ) "FAIL" else "PASS"

    companion object {
        private val mapper: ObjectMapper =
            ObjectMapper()
                .registerModule(
                    com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build(),
                )
                .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Deserializes a consolidated validation report (JSON produced by [toJson]).
         * Nullability mirrors the data class : `xref` is absent when mode was OFF.
         */
        fun fromJson(json: String): DocumentValidationReport =
            mapper.readValue(json, DocumentValidationReport::class.java)
    }
}
