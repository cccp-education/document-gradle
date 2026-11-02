package document

import document.a11y.DocumentA11yAuditor
import document.a11y.DocumentA11ySeverity
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * DOC-A11Y / GBL-011-3 — writes the accessibility report that academy consumes.
 *
 * Thin-wrapper task: all Gradle resolution lives here, the audit is the pure
 * [DocumentA11yAuditor]. It scans the generated artifacts directory
 * (`build/docs/document` by default) and writes `a11y-report.json` next to the
 * other guards.
 *
 * Advisory by default: findings are logged, and only `-Pdocument.a11yStrict=true`
 * turns an `ERROR` into a build failure. The report carries the declared limits
 * (PDF is not PDF/UA), so a reader never assumes a check that did not run.
 */
@DisableCachingByDefault(because = "The accessibility severity branch is side-effect-bearing (strict vs report) — DOC-A11Y")
abstract class ReportDocumentAccessibilityTask : DefaultTask() {
    /** Directory holding the generated HTML/EPUB/PDF artifacts. Tolerant: absent = empty report. */
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:InputFiles
    @get:Optional
    abstract val artifacts: ConfigurableFileCollection

    /** When true, an `ERROR` finding fails the build (default: advisory). */
    @get:Input
    abstract val strict: Property<Boolean>

    /** The report written next to the artifacts. */
    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    init {
        group = "document"
        description = "Écrit le rapport d'accessibilité (a11y-report.json) consommé par academy — DOC-A11Y / GBL-011-3"
    }

    @TaskAction
    fun report() {
        val artifactsDir: File? = artifacts.files.firstOrNull { it.isDirectory }
        val report = DocumentA11yAuditor.audit(artifactsDir)
        reportFile
            .get()
            .asFile
            .apply { parentFile.mkdirs() }
            .writeText(report.toJson())

        val findings = report.deliverables.flatMap { it.findings }
        logger.lifecycle(
            "[document] a11y report: ${report.deliverables.size} deliverable(s), ${findings.size} finding(s) -> " +
                reportFile.get().asFile.absolutePath,
        )
        findings
            .filter { it.severity == DocumentA11ySeverity.ERROR }
            .forEach { finding ->
                logger.warn("[document] a11y ERROR: ${finding.rule} @ ${finding.location} - ${finding.message}")
            }

        val errors = findings.count { it.severity == DocumentA11ySeverity.ERROR }
        if (strict.get() && errors > 0) {
            throw GradleException(
                "[document] accessibility audit failed in strict mode ($errors errors) - " +
                    "see ${reportFile.get().asFile.absolutePath}",
            )
        }
    }
}
