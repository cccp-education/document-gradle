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
 * - conversion security policy advice (DOC-CR5, [document.security.DocumentSecurityPolicy]).
 *
 * Ink Economy Law : [validate] is a deterministic pure function of the source text and the
 * three guard modes — no file is read, no Gradle dependency, fully unit-testable. The caller
 * (the task) performs I/O (read source, write report). The orchestrator only classifies.
 */
object DocumentValidator {

    fun validate(
        text: String,
        baseDir: File,
        includeGuardMode: IncludeGuardMode,
        xrefMode: XrefValidationMode,
        safeMode: SafeMode,
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

        return DocumentValidationReport(includeGuard = includeEntry, xref = xrefEntry, security = securityEntry)
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

data class DocumentValidationReport(
    val includeGuard: IncludeGuardReportEntry,
    val xref: XrefReportEntry?,
    val security: SecurityReportEntry,
) {
    fun toJson(): String =
        ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValueAsString(this)
}
