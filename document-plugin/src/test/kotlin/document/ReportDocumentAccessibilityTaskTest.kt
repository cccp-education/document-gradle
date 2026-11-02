package document

import document.a11y.DocumentA11yReport
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals

/**
 * DOC-A11Y / GBL-011-3 — the `reportDocumentAccessibility` task.
 *
 * The task scans the artifacts directory and writes the report academy consumes.
 * An absent directory is an empty report, never a failure.
 */
class ReportDocumentAccessibilityTaskTest {
    @TempDir
    lateinit var dir: File
    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply("education.cccp.document")
    }

    private fun task(): ReportDocumentAccessibilityTask = project.tasks.getByName("reportDocumentAccessibility") as ReportDocumentAccessibilityTask

    @Test
    fun `the plugin registers the task`() {
        assertEquals("document", project.tasks.getByName("reportDocumentAccessibility").group)
    }

    @Test
    fun `the task writes the report next to the artifacts`() {
        val artifacts = File(dir, "docs").apply { mkdirs() }
        File(artifacts, "document.html").writeText("<html lang=\"fr\"><body><h1>T</h1></body></html>")
        val task =
            task().apply {
                this.artifacts.from(artifacts)
                strict.set(false)
                reportFile.set(project.layout.buildDirectory.file("docs/document/a11y-report.json"))
            }

        task.report()

        val report =
            DocumentA11yReport.fromJson(
                task.reportFile
                    .get()
                    .asFile
                    .readText(),
            )
        assertEquals(1, report.deliverables.size)
        assertEquals("HTML", report.deliverables.first().kind)
    }
}
