package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the *live* `outputFileName` knob
 * (S-235, Option B) : the conversion tasks write
 * `build/docs/document/${outputFileName}.${format.extension}` — the knob was previously
 * dead (declared and wired but never consumed by [ConvertDocumentTask]).
 *
 * Defaults stay backward-compatible: without the CLI override the outputs keep the
 * canonical `document.*` names (all pre-existing tests unaffected).
 */
class ConvertDocumentTaskOutputFileNameFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private val sourceContent = "= Title\n\n[[intro]]\n== Intro\n\nSee <<intro>> here.\n"

    private fun writeBuild() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-output-file-name\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(sourceContent)
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .build()

    @Test
    fun `custom output file name drives the HTML conversion output path`() {
        writeBuild()
        val result = run("convertDocumentToHtml", "-Pdocument.outputFileName=custom")
        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        val custom = projectDir.resolve("build/docs/document/custom.html")
        assertTrue(custom.exists(), "the conversion must write build/docs/document/custom.html when the knob is set")
        assertTrue(custom.readText().contains("id=\"intro\""), "the custom HTML must carry the rendered content")
        assertFalse(
            projectDir.resolve("build/docs/document/document.html").exists(),
            "the canonical document.html must NOT be written when the knob renames the output",
        )
    }

    @Test
    fun `default output file name keeps the canonical html path`() {
        writeBuild()
        val result = run("convertDocumentToHtml")
        assertEquals(TaskOutcome.SUCCESS, result.task(":convertDocumentToHtml")?.outcome)
        assertTrue(
            projectDir.resolve("build/docs/document/document.html").exists(),
            "without the knob the default name stays document.html (backward compat)",
        )
    }
}