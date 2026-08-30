package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-XREF-VALIDATE cross-reference
 * guard wired into [ConvertDocumentTask] (parallel to DOC-CR4 include guard).
 */
class ConvertDocumentTaskXrefValidationFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(converterBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-xref\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.ConvertDocumentTask
            import document.xref.XrefValidationMode

            plugins {
                id("education.cccp.document")
            }

            $converterBlock
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
    fun `STRICT xref validation fails the build on unresolved reference`() {
        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n    converter { xrefValidation = XrefValidationMode.STRICT }\n}",
            sourceContent = "= Title\n\nSee <<missing>> for details.\n",
        )
        val result = runAndFail("convertDocumentToHtml")
        assertTrue(
            result.output.contains("xref validation failed (STRICT)"),
            "la conversion doit échouer sur une référence non résolue (sortie: ${result.output})",
        )
    }

    @Test
    fun `LENIENT xref validation warns but still converts`() {
        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n    converter { xrefValidation = XrefValidationMode.LENIENT }\n}",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        val result = run("convertDocumentToHtml")
        val html = projectDir.resolve("build/docs/document/document.html")
        assertTrue(html.exists(), "la conversion LENIENT doit produire le livrable HTML")
        assertTrue(
            result.output.contains("xref validation (LENIENT)"),
            "la référence non résolue doit être signalée en warn (sortie: ${result.output})",
        )
    }

    @Test
    fun `OFF xref validation (default) converts without auditing`() {
        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n}",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        val result = run("convertDocumentToHtml")
        val html = projectDir.resolve("build/docs/document/document.html")
        assertTrue(html.exists(), "sans xrefValidation configuré, la conversion réussit")
        assertTrue(
            !result.output.contains("xref validation"),
            "en mode OFF (défaut) aucun audit xref n'est journalisé (sortie: ${result.output})",
        )
    }

    @Test
    fun `cli xrefValidation STRICT overrides the DSL LENIENT`() {
        writeBuild(
            converterBlock = "document {\n    source.set(file(\"doc.adoc\"))\n    converter { xrefValidation = XrefValidationMode.LENIENT }\n}",
            sourceContent = "= Title\n\nSee <<missing>> here.\n",
        )
        val result = runAndFail("convertDocumentToHtml", "-Pdocument.xrefValidation=STRICT")
        assertTrue(
            result.output.contains("xref validation failed (STRICT)"),
            "le flag CLI doit primer sur le DSL (sortie: ${result.output})",
        )
    }
}
