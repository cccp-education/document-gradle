package document

import org.asciidoctor.SafeMode
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-VALIDATE-COMPOSITE
 * dedicated task [ValidateDocumentTask] : consolidated report emission + severity
 * (STRICT fails fast, LENIENT warns, OFF skips).
 */
class ValidateDocumentTaskFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(converterBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-validate-task\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.security.IncludeGuardMode
            import document.xref.XrefValidationMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $converterBlock
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

    private fun runAndFail(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .forwardOutput()
        .buildAndFail()

    private val reportPath get() = projectDir.resolve("build/docs/document/document-validation-report.json")

    @Test
    fun `STRICT include escape fails fast but still writes the consolidated report`() {
        writeBuild(
            converterBlock = "converter { includeGuard = IncludeGuardMode.STRICT; safeMode = SafeMode.UNSAFE }",
            sourceContent = "include::/etc/passwd[]\n",
        )
        val result = runAndFail("validateDocument")
        val report = reportPath
        assertTrue(report.exists(), "le rapport consolidé doit être écrit même en STRICT")
        val json = report.readText()
        assertTrue(json.contains("INVALID"), "le rapport doit marquer l'include INVALID")
        assertTrue(json.contains("REJECT"), "le rapport doit marquer la security REJECT (UNSAFE + STRICT)")
        assertTrue(
            result.output.contains("include guard") || result.output.contains("Security policy"),
            "la tâche doit échouer en STRICT (sortie: ${result.output})",
        )
    }

    @Test
    fun `STRICT xref missing fails fast`() {
        writeBuild(
            converterBlock = "converter { xrefValidation = XrefValidationMode.STRICT }",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        val result = runAndFail("validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("MISSING"), "le rapport doit lister la référence manquante")
        assertTrue(
            result.output.contains("xref") || result.output.contains("cross-reference"),
            "la tâche doit échouer sur xref STRICT (sortie: ${result.output})",
        )
    }

    @Test
    fun `LENIENT warns and writes a report without failing`() {
        writeBuild(
            converterBlock = "converter { includeGuard = IncludeGuardMode.LENIENT; xrefValidation = XrefValidationMode.LENIENT; safeMode = SafeMode.UNSAFE }",
            sourceContent = "include::/etc/passwd[]\n\nSee <<missing>> here.\n",
        )
        run("validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("INVALID"), "rapport LENIENT : include INVALID")
        assertTrue(json.contains("MISSING"), "rapport LENIENT : xref MISSING")
        assertTrue(json.contains("WARN"), "rapport LENIENT : security WARN")
    }

    @Test
    fun `OFF skips validation but still emits a VALID report`() {
        writeBuild(
            converterBlock = "",
            sourceContent = "include::/etc/passwd[]\n\nSee <<missing>> here.\n",
        )
        run("validateDocument")
        val json = reportPath.readText()
        assertTrue(json.contains("VALID"), "en OFF le rapport consolide marque VALID partout")
        assertFalse(json.contains("MISSING"), "en OFF le xref n'est pas audité")
        assertFalse(json.contains("INVALID"), "en OFF l'include n'est pas audité")
    }
}
