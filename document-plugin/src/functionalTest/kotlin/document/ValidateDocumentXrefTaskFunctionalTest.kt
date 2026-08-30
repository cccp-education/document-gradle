package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-XREF-VALIDATE dedicated
 * task [ValidateDocumentXrefTask] : report emission + severity (STRICT fails,
 * LENIENT warns, OFF skips).
 */
class ValidateDocumentXrefTaskFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(xrefBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-xref-task\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.xref.XrefValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $xrefBlock
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

    @Test
    fun `STRICT writes the report then fails the build on unresolved reference`() {
        writeBuild(
            xrefBlock = "converter { xrefValidation = XrefValidationMode.STRICT }",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        val result = runAndFail("validateDocumentXref")
        val report = projectDir.resolve("build/docs/document/xref-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit même en STRICT")
        assertTrue(
            report.readText().contains("MISSING") && report.readText().contains("missing"),
            "le rapport doit lister la référence manquante 'missing'",
        )
        assertTrue(
            result.output.contains("xref validation failed (STRICT)"),
            "la tâche doit échouer en STRICT (sortie: ${result.output})",
        )
    }

    @Test
    fun `LENIENT writes a VALID report when all references resolve`() {
        writeBuild(
            xrefBlock = "converter { xrefValidation = XrefValidationMode.LENIENT }",
            sourceContent = "[[intro]]Intro\n\nSee <<intro>> here.\n",
        )
        run("validateDocumentXref")
        val report = projectDir.resolve("build/docs/document/xref-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit en LENIENT")
        assertTrue(
            report.readText().contains("VALID"),
            "le rapport doit marquer VALID quand tout résout",
        )
    }

    @Test
    fun `OFF skips validation without writing a report`() {
        writeBuild(
            xrefBlock = "",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        run("validateDocumentXref")
        val report = projectDir.resolve("build/docs/document/xref-validation-report.json")
        assertTrue(!report.exists(), "en mode OFF aucun rapport n'est produit")
    }
}
