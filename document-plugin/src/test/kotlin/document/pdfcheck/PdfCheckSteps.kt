package document.pdfcheck

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@pdf-check` scenarios (DOC-PDF-CHECK).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `converter { pdfCheck = ... }` block, run
 * `validateDocumentPdf` (optionally after `convertDocumentToPdf`), and assert
 * the outcome (skip / warn / reject / report). All step texts carry the
 * `pdf-check` prefix (anti-glue pattern from S-223) and are deliberately
 * distinct from the epub-check steps.
 */
class PdfCheckSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String
    private lateinit var reportFile: File

    @Given("a document gradle project with pdfCheck {string} and source {string}")
    fun `a project with pdfCheck and source`(mode: String, source: String) {
        projectDir = Files.createTempDirectory("doc-pdf-check-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-pdf-check\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.pdf.PdfValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                converter {
                    pdfCheck = PdfValidationMode.$mode
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(source)
        reportFile = projectDir.resolve("build/docs/document/pdf-validation-report.json")
    }

    @When("the validateDocumentPdf pdf-check task runs successfully")
    fun `validateDocumentPdf runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentPdf")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocumentPdf pdf-check task runs and fails")
    fun `validateDocumentPdf runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentPdf")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @When("the PDF converted and validated with the pdf-check tasks")
    fun `pdf converted then validated`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToPdf", "validateDocumentPdf")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @Then("no pdf-validation-report.json is written")
    fun `no pdf report written`() {
        assertTrue(!reportFile.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Then("the build fails with pdf validation message {string}")
    fun `build fails with pdf validation message`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "la configuration STRICT doit rejeter la validation (sortie: $buildOutput)",
        )
    }

    @Then("the pdf-check report marks the finding {string}")
    fun `pdf report marks the finding`(finding: String) {
        assertTrue(reportFile.exists(), "le rapport JSON doit être écrit")
        assertTrue(reportFile.readText().contains(finding), "le rapport doit marquer '$finding'")
    }
}