package document.epubcheck

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@epub-check` scenarios (DOC-EPUBCHECK).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that
 * applies the document plugin with a `converter { epubCheck = ... }` block, run
 * `validateDocumentEpub` (optionally after `convertDocumentToEpub`), and assert
 * the outcome (skip / warn / reject / report). All step texts carry the
 * `epub-check` prefix (anti-glue pattern from S-223).
 */
class EpubCheckSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String
    private lateinit var reportFile: File

    @Given("a document gradle project with epubCheck {string} and source {string}")
    fun `a project with epubCheck and source`(mode: String, source: String) {
        projectDir = Files.createTempDirectory("doc-epub-check-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-epub-check\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            import document.epub.EpubValidationMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                converter {
                    epubCheck = EpubValidationMode.$mode
                }
            }
            """.trimIndent(),
        )
        projectDir.resolve("doc.adoc").writeText(source)
        reportFile = projectDir.resolve("build/docs/document/epub-validation-report.json")
    }

    @When("the validateDocumentEpub epub-check task runs successfully")
    fun `validateDocumentEpub runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentEpub")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocumentEpub epub-check task runs and fails")
    fun `validateDocumentEpub runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocumentEpub")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @When("the EPUB converted and validated with the epub-check tasks")
    fun `epub converted then validated`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToEpub", "validateDocumentEpub")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @Then("no epub-validation-report.json is written")
    fun `no report written`() {
        assertTrue(!reportFile.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Then("the build fails with epub validation message {string}")
    fun `build fails with epub validation message`(message: String) {
        assertTrue(
            buildOutput.contains(message),
            "la configuration STRICT doit rejeter la validation (sortie: $buildOutput)",
        )
    }

    @Then("the epub-check report marks the finding {string}")
    fun `report marks the finding`(finding: String) {
        assertTrue(reportFile.exists(), "le rapport JSON doit être écrit")
        assertTrue(reportFile.readText().contains(finding), "le rapport doit marquer '$finding'")
    }
}