package document

import document.security.IncludeGuardMode
import document.validation.DocumentValidator
import document.xref.XrefValidationMode
import org.asciidoctor.SafeMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory

/**
 * Pre-flight composite validation of an AsciiDoc document (DOC-VALIDATE-COMPOSITE).
 *
 * Composes the conversion-time guards into a single standalone pass :
 * - include-path guard ([document.security.IncludePathValidator], DOC-CR4) ;
 * - cross-reference validation ([document.xref.XrefValidator], DOC-XREF-VALIDATE) ;
 * - conversion security policy advice ([document.security.DocumentSecurityPolicy], DOC-CR5).
 *
 * The three guard modes are read from the unified `converter { }` DSL (and their flat
 * mirrors / CLI `-Pdocument.*` overrides). Severity follows the same contract as the
 * conversion task :
 * - [IncludeGuardMode.STRICT] / [XrefValidationMode.STRICT] invalid finding → fail-fast
 *   [GradleException] after writing the consolidated report ;
 * - [IncludeGuardMode.LENIENT] / [XrefValidationMode.LENIENT] → warn, build succeeds ;
 * - [IncludeGuardMode.OFF] / [XrefValidationMode.OFF] → no-op (report still written, VALID).
 * - security advice [document.security.SecurityAdvice.Reject] (STRICT include guard +
 *   UNSAFE SafeMode) → fail-fast.
 *
 * Ink Economy Law: the validator is a pure function of the text; only report emission and
 * the severity branches are side-effect-bearing.
 */
abstract class ValidateDocumentTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val includeGuard: Property<IncludeGuardMode>

    @get:Input
    abstract val xrefValidation: Property<XrefValidationMode>

    @get:Input
    abstract val safeMode: Property<SafeMode>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    private val log = LoggerFactory.getLogger(ValidateDocumentTask::class.java)

    @TaskAction
    fun validate() {
        val source = sourceFile.get().asFile
        if (!source.exists()) {
            log.warn("{} — source absente : {}", name, source.absolutePath)
            return
        }

        val includeMode = includeGuard.get()
        val xrefMode = xrefValidation.get()
        val safe = safeMode.get()

        val report = DocumentValidator.validate(source.readText(), source.parentFile, includeMode, xrefMode, safe)
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.toJson())
        }

        var failed = false

        if (includeMode == IncludeGuardMode.STRICT && report.includeGuard.status == "INVALID") {
            failed = true
            log.error(
                "{} — include guard (STRICT) rejected: {} at line {}",
                name,
                report.includeGuard.reason,
                report.includeGuard.line,
            )
        } else if (includeMode == IncludeGuardMode.LENIENT && report.includeGuard.status == "INVALID") {
            log.warn(
                "{} — include guard (LENIENT): {} at line {}",
                name,
                report.includeGuard.reason,
                report.includeGuard.line,
            )
        }

        if (xrefMode == XrefValidationMode.STRICT && report.xref?.status == "MISSING") {
            failed = true
            log.error(
                "{} — xref validation (STRICT) failed: {} unresolved cross-reference(s): {}",
                name,
                report.xref.missing.size,
                report.xref.missing.joinToString(),
            )
        } else if (xrefMode == XrefValidationMode.LENIENT && report.xref?.status == "MISSING") {
            log.warn(
                "{} — xref validation (LENIENT): {} unresolved cross-reference(s): {}",
                name,
                report.xref.missing.size,
                report.xref.missing.joinToString(),
            )
        }

        if (report.security.advice == "REJECT") {
            failed = true
            log.error("{} — Security policy (STRICT) rejected conversion — {}", name, report.security.reason)
        } else if (report.security.advice == "WARN") {
            log.warn("{} — Security policy (LENIENT) — {}", name, report.security.reason)
        }

        if (failed) {
            throw GradleException("document validation failed (STRICT) — see ${reportFile.get().asFile.absolutePath}")
        }
        log.info("{} — validation terminée (rapport: {})", name, reportFile.get().asFile.absolutePath)
    }
}
