package document

import document.security.IncludeGuardMode
import document.validation.DocumentValidator
import document.validation.HtmlLinkLintMode
import document.xref.XrefValidationMode
import org.asciidoctor.SafeMode
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Pre-flight composite validation of an AsciiDoc document (DOC-VALIDATE-COMPOSITE).
 *
 * Composes the conversion-time guards into a single standalone pass :
 * - include-path guard ([document.security.IncludePathValidator], DOC-CR4) ;
 * - cross-reference validation ([document.xref.XrefValidator], DOC-XREF-VALIDATE) ;
 * - conversion security policy advice ([document.security.DocumentSecurityPolicy], DOC-CR5) ;
 * - HTML link lint of the rendered output ([document.validation.HtmlLinkLinter], DOC-HTML-LINT —
 *   fourth guard, DOC-VALIDATE-HTML-LINT).
 *
 * The guard modes are read from the unified `converter { }` DSL (and their flat
 * mirrors / CLI `-Pdocument.*` overrides). Severity follows the same contract as the
 * conversion task :
 * - [IncludeGuardMode.STRICT] / [XrefValidationMode.STRICT] / [HtmlLinkLintMode.STRICT]
 *   invalid finding → fail-fast [GradleException] after writing the consolidated report ;
 * - LENIENT → warn, build succeeds ;
 * - OFF → no-op (report still written, VALID).
 * - security advice [document.security.SecurityAdvice.Reject] (STRICT include guard +
 *   UNSAFE SafeMode) → fail-fast.
 *
 * The fourth guard reads the *rendered* HTML ([htmlFile], produced by
 * `convertDocumentToHtml`). The task declares `mustRunAfter("convertDocumentToHtml")` so an
 * explicit `./gradlew validateDocument convertDocumentToHtml` lints the fresh HTML, while a
 * standalone `validateDocument` with a non-OFF lint mode but no HTML on disk reports `DEAD`
 * with a `<html-file-missing>` entry instead of silently skipping (visibility over silence).
 *
 * Ink Economy Law: the validator is a pure function of the texts; only report emission and
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

    @get:Input
    abstract val htmlLinkLint: Property<HtmlLinkLintMode>

    /**
     * Path of the rendered HTML audited by the fourth guard, carried as an [@Input] string
     * (not an [@InputFile]) : the file is produced by `convertDocumentToHtml`, which this task
     * must NOT implicitly depend on — an absent file is a reported `<html-file-missing>`
     * finding, never a validation error.
     */
    @get:Input
    @get:Optional
    abstract val htmlFilePath: Property<String>

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
        val lintMode = htmlLinkLint.get()

        val html: String? = if (lintMode == HtmlLinkLintMode.OFF) {
            null
        } else {
            htmlFilePath.orNull?.let { File(it) }?.takeIf { it.exists() }?.readText()
        }

        val report = DocumentValidator.validate(
            source.readText(), source.parentFile, includeMode, xrefMode, safe, lintMode, html,
        )
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

        // Fourth guard severity (DOC-VALIDATE-HTML-LINT) — same contract as xref : STRICT
        // fails fast (after the report is written), LENIENT warns, OFF is not audited.
        if (lintMode == HtmlLinkLintMode.STRICT && report.htmlLint?.status == "DEAD") {
            failed = true
            log.error(
                "{} — HTML link lint (STRICT) failed: {} dead internal link(s): {}",
                name,
                report.htmlLint.deadLinks.orEmpty().size,
                report.htmlLint.deadLinks.orEmpty().joinToString(),
            )
        } else if (lintMode == HtmlLinkLintMode.LENIENT && report.htmlLint?.status == "DEAD") {
            log.warn(
                "{} — HTML link lint (LENIENT): {} dead internal link(s): {}",
                name,
                report.htmlLint.deadLinks.orEmpty().size,
                report.htmlLint.deadLinks.orEmpty().joinToString(),
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
