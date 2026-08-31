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
 * Functional tests (TestKit, real Gradle run) for the N3 collect following the
 * live `outputFileName` knob (S-236 follow-up of S-235) : `collectDocumentRetrieve`
 * indexes the artifacts under their real name
 * `build/docs/document/${outputFileName}.${ext}` in `composite-context.json`.
 * Default `"document"` keeps the canonical names — fully backward compatible.
 */
class CollectDocumentRetrieveOutputFileNameFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-collect-output-file-name\"\n")
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
        projectDir.resolve("doc.adoc").writeText(
            """
            = Title

            == Intro

            Collect custom output content.
            """.trimIndent(),
        )
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .build()

    @Test
    fun `collect indexes the custom html artifact when the knob renames the conversion output`() {
        writeBuild()
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "-Pdocument.outputFileName=custom")
            .withPluginClasspath()
            .build()

        val result = run("collectDocumentRetrieve", "-Pdocument.outputFileName=custom")
        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)

        val composite = projectDir.resolve("build/docs/document/composite-context.json").readText()
        assertTrue(
            composite.contains("custom.html"),
            "composite-context.json must reference the custom-named artifact — actual: $composite",
        )
        assertFalse(
            composite.contains("\"document.html\""),
            "the canonical document.html must NOT be indexed when the knob renames the output",
        )
        assertTrue(
            projectDir.resolve("build/docs/document/metadata.json").exists(),
            "metadata.json must still be produced beside the composite context",
        )
    }

    @Test
    fun `collect with default output file name keeps indexing the canonical html artifact`() {
        writeBuild()
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml")
            .withPluginClasspath()
            .build()

        val result = run("collectDocumentRetrieve")
        assertEquals(TaskOutcome.SUCCESS, result.task(":collectDocumentRetrieve")?.outcome)

        val composite = projectDir.resolve("build/docs/document/composite-context.json").readText()
        assertTrue(
            composite.contains("document.html"),
            "without the knob the canonical document.html stays indexed (backward compat)",
        )
    }
}