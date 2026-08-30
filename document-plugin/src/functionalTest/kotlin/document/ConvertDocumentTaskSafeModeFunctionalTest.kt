package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-CR3-2 converter
 * safe-mode guard wiring: the `converter { safeMode = ... }` DSL block (and the
 * flat `safeMode` mirror) plus the `-Pdocument.safeMode` CLI flag must be
 * propagated to every [ConvertDocumentTask].
 */
class ConvertDocumentTaskSafeModeFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(converterBlock: String, cliSafeModeImport: Boolean = true) {
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-converter-safemode"
            """.trimIndent(),
        )
        val imports = if (cliSafeModeImport) "import org.asciidoctor.SafeMode\n" else ""
        projectDir.resolve("build.gradle.kts").writeText(
            """
            $imports
            import document.ConvertDocumentTask

            plugins {
                id("education.cccp.document")
            }

            $converterBlock

            val htmlTask = tasks.named("convertDocumentToHtml", ConvertDocumentTask::class.java)
            tasks.register("printSafeMode") {
                doLast { println("SAFEMODE=" + htmlTask.get().safeMode.get()) }
            }
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
    fun `converter DSL safeMode SERVER is propagated to convertDocumentToHtml`() {
        writeBuild(
            converterBlock = "document {\n    converter { safeMode = SafeMode.SERVER }\n}",
        )

        val result = run("printSafeMode", "-q")

        assertTrue(
            result.output.contains("SAFEMODE=SERVER"),
            "le safeMode du block converter doit etre propagé à la tâche (sortie: ${result.output})",
        )
    }

    @Test
    fun `converter DSL safeMode SECURE is propagated to convertDocumentToHtml`() {
        writeBuild(
            converterBlock = "document {\n    converter { safeMode = SafeMode.SECURE }\n}",
        )

        val result = run("printSafeMode", "-q")

        assertTrue(
            result.output.contains("SAFEMODE=SECURE"),
            "le safeMode SECURE du block converter doit etre propagé (sortie: ${result.output})",
        )
    }

    @Test
    fun `cliSafeMode SERVER overrides the DSL`() {
        writeBuild(
            converterBlock = "document {\n    converter { safeMode = SafeMode.UNSAFE }\n}",
        )

        val result = run("printSafeMode", "-q", "-Pdocument.safeMode=SERVER")

        assertTrue(
            result.output.contains("SAFEMODE=SERVER"),
            "le flag CLI doit primer sur le DSL (sortie: ${result.output})",
        )
    }

    @Test
    fun `default safeMode is UNSAFE when nothing is configured`() {
        writeBuild(
            converterBlock = "",
        )

        val result = run("printSafeMode", "-q")

        assertTrue(
            result.output.contains("SAFEMODE=UNSAFE"),
            "le defaut doit rester UNSAFE (backward-compat) (sortie: ${result.output})",
        )
    }
}
