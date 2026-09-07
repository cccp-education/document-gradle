package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-PDF-CHECK dedicated
 * task [ValidateDocumentPdfTask] : report emission + severity (STRICT fails,
 * LENIENT warns, OFF skips) + `<pdf-file-missing>` rule (mirror of the EPUB
 * S-240 rule). The STRICT-valid scenario converts a real PDF through
 * `convertDocumentToPdf` inside the test build (no versioned binary, Ink
 * Economy Law).
 */
class ValidateDocumentPdfTaskFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(pdfBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-pdf-task\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.pdf.PdfValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $pdfBlock
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
    fun `OFF skips validation without writing a report`() {
        writeBuild(
            pdfBlock = "",
            sourceContent = "= Title\n\nHello.\n",
        )
        run("validateDocumentPdf")
        val report = projectDir.resolve("build/docs/document/pdf-validation-report.json")
        assertTrue(!report.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Test
    fun `missing PDF under STRICT fails with the pdf-file-missing finding`() {
        writeBuild(
            pdfBlock = "converter { pdfCheck = PdfValidationMode.STRICT }",
            sourceContent = "= Title\n\nHello.\n",
        )
        val result = runAndFail("validateDocumentPdf")
        val report = projectDir.resolve("build/docs/document/pdf-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit même quand le PDF est absent")
        assertTrue(
            report.readText().contains("<pdf-file-missing>"),
            "le rapport doit porter le marqueur pdf-file-missing",
        )
        assertTrue(
            result.output.contains("pdf validation failed (STRICT)"),
            "STRICT doit rejeter le PDF absent (sortie: ${result.output})",
        )
    }

    @Test
    fun `missing PDF under LENIENT warns but keeps the build green`() {
        writeBuild(
            pdfBlock = "converter { pdfCheck = PdfValidationMode.LENIENT }",
            sourceContent = "= Title\n\nHello.\n",
        )
        val result = run("validateDocumentPdf")
        assertTrue(
            result.output.contains("pdf validation failed (LENIENT)"),
            "LENIENT doit visibiliser le PDF absent en warn (sortie: ${result.output})",
        )
        val report = projectDir.resolve("build/docs/document/pdf-validation-report.json")
        assertTrue(report.exists(), "le rapport doit être écrit en LENIENT")
        assertTrue(report.readText().contains("<pdf-file-missing>"), "le rapport marque pdf-file-missing")
    }

    @Test
    fun `STRICT accepts a freshly converted PDF`() {
        writeBuild(
            pdfBlock = "converter { pdfCheck = PdfValidationMode.STRICT }",
            sourceContent = "= Book\n\n== Chapter One\n\nHello pdfcheck.\n",
        )
        run("convertDocumentToPdf", "validateDocumentPdf")
        val report = projectDir.resolve("build/docs/document/pdf-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit")
        assertTrue(
            report.readText().contains("VALID"),
            "le PDF fraîchement converti doit passer la validation structurelle (rapport: ${report.readText()})",
        )
    }
}