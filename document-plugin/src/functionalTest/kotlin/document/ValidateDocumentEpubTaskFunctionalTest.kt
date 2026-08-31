package document

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the DOC-EPUBCHECK dedicated
 * task [ValidateDocumentEpubTask] : report emission + severity (STRICT fails,
 * LENIENT warns, OFF skips) + `<epub-file-missing>` rule (mirror of the S-232
 * HTML rule). The STRICT-valid scenario converts a real EPUB through
 * `convertDocumentToEpub` inside the test build (cadrage decision 10 — no
 * versioned binary, Ink Economy Law).
 */
class ValidateDocumentEpubTaskFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writeBuild(epubBlock: String, sourceContent: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-epub-task\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.epub.EpubValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $epubBlock
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
            epubBlock = "",
            sourceContent = "= Title\n\nHello.\n",
        )
        run("validateDocumentEpub")
        val report = projectDir.resolve("build/docs/document/epub-validation-report.json")
        assertTrue(!report.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Test
    fun `missing EPUB under STRICT fails with the epub-file-missing finding`() {
        writeBuild(
            epubBlock = "converter { epubCheck = EpubValidationMode.STRICT }",
            sourceContent = "= Title\n\nHello.\n",
        )
        val result = runAndFail("validateDocumentEpub")
        val report = projectDir.resolve("build/docs/document/epub-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit même quand l'EPUB est absent")
        assertTrue(
            report.readText().contains("<epub-file-missing>"),
            "le rapport doit porter le marqueur epub-file-missing",
        )
        assertTrue(
            result.output.contains("epub validation failed (STRICT)"),
            "STRICT doit rejeter l'EPUB absent (sortie: ${result.output})",
        )
    }

    @Test
    fun `missing EPUB under LENIENT warns but keeps the build green`() {
        writeBuild(
            epubBlock = "converter { epubCheck = EpubValidationMode.LENIENT }",
            sourceContent = "= Title\n\nHello.\n",
        )
        val result = run("validateDocumentEpub")
        assertTrue(
            result.output.contains("epub validation failed (LENIENT)"),
            "LENIENT doit visibiliser l'EPUB absent en warn (sortie: ${result.output})",
        )
        val report = projectDir.resolve("build/docs/document/epub-validation-report.json")
        assertTrue(report.exists(), "le rapport doit être écrit en LENIENT")
        assertTrue(report.readText().contains("<epub-file-missing>"), "le rapport marque epub-file-missing")
    }

    @Test
    fun `STRICT accepts a freshly converted EPUB`() {
        writeBuild(
            epubBlock = "converter { epubCheck = EpubValidationMode.STRICT }",
            sourceContent = "= Book\n\n== Chapter One\n\nHello epubcheck.\n",
        )
        run("convertDocumentToEpub", "validateDocumentEpub")
        val report = projectDir.resolve("build/docs/document/epub-validation-report.json")
        assertTrue(report.exists(), "le rapport JSON doit être écrit")
        assertTrue(
            report.readText().contains("VALID"),
            "l'EPUB fraîchement converti doit passer epubcheck (rapport: ${report.readText()})",
        )
    }
}