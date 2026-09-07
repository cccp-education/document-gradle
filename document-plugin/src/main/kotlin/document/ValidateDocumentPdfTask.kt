package document

import document.pdf.PdfValidationMode
import document.pdf.PdfValidationReport
import document.pdf.PdfValidationResult
import document.pdf.PdfBoxValidatorAdapter
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.slf4j.LoggerFactory
import java.io.File

/**
 * Validates the PDF artifact produced by `convertDocumentToPdf` with Apache
 * PDFBox and emits a JSON report (DOC-PDF-CHECK).
 *
 * Sixth document guard — parallel to the five existing guards (includeGuard /
 * xref / security / htmlLint / epubCheck) which audit the AsciiDoc source, the
 * rendered HTML or the EPUB binary; this one audits the PDF *binary*
 * post-conversion (structure: loadable, non-empty, extractable text per page).
 * The [pdfCheck] mode drives the severity:
 * - [PdfValidationMode.OFF]     : no-op (default, backward-compatible) ;
 * - [PdfValidationMode.LENIENT] : PDF findings are logged as warnings, the
 *   report is still written, the build succeeds ;
 * - [PdfValidationMode.STRICT]  : any PDF finding fails the build with a
 *   [GradleException] (fail-fast) after writing the report.
 *
 * The report lands next to the other guards in `build/docs/document/`. Ink
 * Economy Law: the validation is a pure function of the file; severity is the
 * only side-effect-bearing branch.
 */
@DisableCachingByDefault(because = "PDF validation severity branches are side-effect-bearing (fail-fast vs report)")
abstract class ValidateDocumentPdfTask : DefaultTask() {

    /**
     * Path of the PDF artifact audited by this guard, carried as an [@Input]
     * string (NOT an [@InputFile]) — the exact [ValidateDocumentEpubTask.epubFile]
     * shape from S-240: the file is produced by `convertDocumentToPdf`, which this
     * task must NOT implicitly depend on; an absent file is a reported
     * `<pdf-file-missing>` finding, never a configuration-time validation error
     * (pitfall: `@InputFile` validates existence BEFORE the action runs).
     */
    @get:Input
    @get:Optional
    abstract val pdfFile: Property<String>

    @get:Input
    abstract val pdfCheck: Property<PdfValidationMode>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    private val log = LoggerFactory.getLogger(ValidateDocumentPdfTask::class.java)

    @TaskAction
    fun validate() {
        val mode = pdfCheck.get()
        if (mode == PdfValidationMode.OFF) {
            log.info("{} — pdf validation disabled (OFF), skipping", name)
            return
        }

        val pdf = pdfFile.orNull?.let { File(it) }
        if (pdf == null || !pdf.exists() || !pdf.isFile) {
            // Mirror of the S-232/S-240 rule: visibility over silence — a missing
            // PDF under a non-OFF mode is an audit finding, never a silent skip.
            val label = pdf?.name ?: "document.pdf"
            val report = PdfValidationReport.fromResult(
                PdfValidationResult.Invalid(listOf("<pdf-file-missing> $label")),
            )
            writeReport(report)
            val message = message(mode, "<pdf-file-missing> $label")
            if (mode == PdfValidationMode.STRICT) {
                throw GradleException(message)
            }
            log.warn("{} — {}", name, message)
            return
        }

        val result = PdfBoxValidatorAdapter().validate(pdf)
        val report = PdfValidationReport.fromResult(result)
        writeReport(report)

        val issues = result.issues
        if (issues.isEmpty()) {
            log.info("{} — PDF passed the structural checks ({}): {}", name, pdf.name, reportFile.get().asFile.absolutePath)
            return
        }
        when (mode) {
            PdfValidationMode.LENIENT -> log.warn("{} — {} PDF issue(s): {}", name, issues.size, issues.joinToString())
            PdfValidationMode.STRICT -> throw GradleException(message(mode, issues.joinToString()))
            PdfValidationMode.OFF -> Unit
        }
    }

    private fun writeReport(report: PdfValidationReport) {
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.toJson())
        }
    }

    private fun message(mode: PdfValidationMode, detail: String): String =
        "pdf validation failed ($mode): $detail"
}