package document

import document.epub.EpubValidationMode
import document.epub.EpubValidationReport
import document.epub.EpubValidationResult
import document.epub.LibEpubCheckAdapter
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
 * Validates the EPUB artifact produced by `convertDocumentToEpub` with the W3C
 * `epubcheck` library and emits a JSON report (DOC-EPUBCHECK).
 *
 * Fifth document guard — parallel to the four composite guards (includeGuard /
 * xref / security / htmlLint) which audit the AsciiDoc source or the rendered
 * HTML; this one audits the EPUB *binary* post-conversion. The [epubCheck] mode
 * drives the severity:
 * - [EpubValidationMode.OFF]     : no-op (default, backward-compatible) ;
 * - [EpubValidationMode.LENIENT] : EPUB issues are logged as warnings, the report
 *   is still written, the build succeeds ;
 * - [EpubValidationMode.STRICT]  : any EPUB issue fails the build with a
 *   [GradleException] (fail-fast) after writing the report.
 *
 * The report lands next to the other guards in `build/docs/document/`. Ink
 * Economy Law: the validation is a pure function of the file; severity is the
 * only side-effect-bearing branch.
 */
@DisableCachingByDefault(because = "EPUB validation severity branches are side-effect-bearing (fail-fast vs report)")
abstract class ValidateDocumentEpubTask : DefaultTask() {

    /**
     * Path of the EPUB artifact audited by this guard, carried as an [@Input]
     * string (NOT an [@InputFile]) — the exact [ValidateDocumentTask.htmlFilePath]
     * shape from S-232: the file is produced by `convertDocumentToEpub`, which this
     * task must NOT implicitly depend on; an absent file is a reported
     * `<epub-file-missing>` finding, never a configuration-time validation error
     * (pitfall: `@InputFile` validates existence BEFORE the action runs).
     */
    @get:Input
    @get:Optional
    abstract val epubFile: Property<String>

    @get:Input
    abstract val epubCheck: Property<EpubValidationMode>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    private val log = LoggerFactory.getLogger(ValidateDocumentEpubTask::class.java)

    @TaskAction
    fun validate() {
        val mode = epubCheck.get()
        if (mode == EpubValidationMode.OFF) {
            log.info("{} — epub validation disabled (OFF), skipping", name)
            return
        }

        val epub = epubFile.orNull?.let { File(it) }
        if (epub == null || !epub.exists() || !epub.isFile) {
            // Mirror of the S-232 HTML rule: visibility over silence — a missing
            // EPUB under a non-OFF mode is an audit finding, never a silent skip.
            val label = epub?.name ?: "document.epub"
            val report = EpubValidationReport.fromResult(
                EpubValidationResult.Invalid(listOf("<epub-file-missing> $label")),
            )
            writeReport(report)
            val message = message(mode, "<epub-file-missing> $label")
            if (mode == EpubValidationMode.STRICT) {
                throw GradleException(message)
            }
            log.warn("{} — {}", name, message)
            return
        }

        val result = LibEpubCheckAdapter().validate(epub)
        val report = EpubValidationReport.fromResult(result)
        writeReport(report)

        val issues = result.issues
        if (issues.isEmpty()) {
            log.info("{} — EPUB passed epubcheck ({}): {}", name, epub.name, reportFile.get().asFile.absolutePath)
            return
        }
        when (mode) {
            EpubValidationMode.LENIENT -> log.warn("{} — {} EPUB issue(s): {}", name, issues.size, issues.joinToString())
            EpubValidationMode.STRICT -> throw GradleException(message(mode, issues.joinToString()))
            EpubValidationMode.OFF -> Unit
        }
    }

    private fun writeReport(report: EpubValidationReport) {
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.toJson())
        }
    }

    private fun message(mode: EpubValidationMode, detail: String): String =
        "epub validation failed ($mode): $detail"
}