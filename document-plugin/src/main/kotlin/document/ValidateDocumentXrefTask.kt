package document

import document.xref.XrefValidationMode
import document.xref.XrefValidationReport
import document.xref.XrefValidationResult
import document.xref.XrefValidator
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
 * Validates cross-references (`<<id>>` / `xref:id[]`) in an AsciiDoc document and
 * emits a JSON report (DOC-XREF-VALIDATE).
 *
 * Parallel to [document.security.DocumentIncludeGuard] : a pure pre-flight audit
 * of the source text. The [xrefValidation] mode drives the severity :
 * - [XrefValidationMode.OFF]     : no-op (default, backward-compatible) ;
 * - [XrefValidationMode.LENIENT] : unresolved references are logged as warnings, the
 *   report is still written, the build succeeds ;
 * - [XrefValidationMode.STRICT]  : any unresolved reference fails the build with a
 *   [GradleException] (fail-fast) after writing the report.
 *
 * Ink Economy Law: the validator is a pure function of the text; severity is the
 * only side-effect-bearing branch.
 */
abstract class ValidateDocumentXrefTask : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val sourceFile: RegularFileProperty

    @get:Input
    abstract val xrefValidation: Property<XrefValidationMode>

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    private val log = LoggerFactory.getLogger(ValidateDocumentXrefTask::class.java)

    @TaskAction
    fun validate() {
        val mode = xrefValidation.get()
        if (mode == XrefValidationMode.OFF) {
            log.info("{} — xref validation disabled (OFF), skipping", name)
            return
        }

        val source = sourceFile.get().asFile
        if (!source.exists()) {
            log.warn("{} — source absente : {}", name, source.absolutePath)
            return
        }

        val result = XrefValidator.validate(source.readText())
        val report = XrefValidationReport.fromResult(result)
        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(report.toJson())
        }

        when (mode) {
            XrefValidationMode.LENIENT -> {
                if (result is XrefValidationResult.Invalid) {
                    log.warn(
                        "{} — {} unresolved cross-reference(s): {}",
                        name,
                        result.missing.size,
                        result.missing.joinToString(),
                    )
                }
            }
            XrefValidationMode.STRICT -> {
                if (result is XrefValidationResult.Invalid) {
                    throw GradleException(
                        "xref validation failed (STRICT): ${result.missing.size} unresolved " +
                            "cross-reference(s): ${result.missing.joinToString()}",
                    )
                }
            }
            XrefValidationMode.OFF -> Unit
        }
    }
}
