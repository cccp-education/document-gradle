package document.xrefvalidation

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@xref-validation` scenarios (DOC-XREF-VALIDATE).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `converter { xrefValidation = ... }` block
 * and the given source, run `validateDocumentXref`, and assert the outcome
 * (skip / warn / reject / report). All step texts carry the `xrefvalidation`
 * prefix (anti-glue pattern from S-223).
 */
class XrefValidationSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String
    private lateinit var reportFile: File

    @Given("a document gradle project with xrefValidation {string} and source {string}")
    fun `a project with xrefValidation and source`(mode: String, source: String) {
        projectDir = Files.createTempDirectory("doc-xref-validation-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-xref-validation\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.xref.XrefValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                converter {
                    xrefValidation = XrefValidationMode.$mode
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(source)
        reportFile = projectDir.resolve("build/docs/document/xref-validation-report.json")
    }

    @When("the validateDocumentXref task runs successfully")
    fun `validateDocumentXref runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentXref")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocumentXref task runs and fails")
    fun `validateDocumentXref runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentXref")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @Then("no xref-validation-report.json is written")
    fun `no report written`() {
        assertTrue(!reportFile.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Then("the build succeeds with xref validation warning")
    fun `build succeeds with xref validation warning`() {
        assertTrue(
            buildOutput.contains("xref validation (LENIENT)"),
            "la référence non résolue doit être signalée en warn (sortie: $buildOutput)",
        )
    }

    @Then("the report marks VALID")
    fun `report marks VALID`() {
        assertTrue(reportFile.exists(), "le rapport JSON doit être écrit")
        assertTrue(reportFile.readText().contains("VALID"), "le rapport doit marquer VALID")
    }

    @Then("the build fails with xref validation message {string}")
    fun `build fails with xref validation message`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "la configuration STRICT doit rejeter la validation (sortie: $buildOutput)",
        )
    }

    @Then("the report lists the missing reference {string}")
    fun `report lists missing reference`(ref: String) {
        assertTrue(reportFile.exists(), "le rapport JSON doit être écrit même en STRICT")
        val json = reportFile.readText()
        assertTrue(json.contains("MISSING") && json.contains(ref), "le rapport doit lister '$ref'")
    }
}
