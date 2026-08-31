package document.metadatavalidation

import document.security.IncludeGuardMode
import document.validation.DocumentValidationReport
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@metadata-validation` scenarios (DOC-METADATA-VALIDATION).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script applying the
 * document plugin, run the composite validation (when required) then `collectDocumentRetrieve`,
 * and assert that metadata.json carries the derived `validationStatus`
 * (PASS / FAIL / omitted). Every step text carries the `metadata-validation` prefix
 * (anti-glue pattern from S-223).
 */
class MetadataValidationSteps {

    private lateinit var projectDir: File
    private lateinit var metadataFile: File

    @Given("a document gradle project with metadata-validation config {string} and source {string}")
    fun `a document gradle project with metadata-validation config and source`(config: String, source: String) {
        projectDir = Files.createTempDirectory("doc-metadata-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-metadata\"\n")
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
                converter {
                    includeGuard = IncludeGuardMode.${includeMode(config)}
                    xrefValidation = XrefValidationMode.STRICT
                    safeMode = SafeMode.${safeMode(config)}
                }
            }
            """.trimIndent()
        )
        projectDir.resolve("doc.adoc").writeText(source)
        metadataFile = projectDir.resolve("build/docs/document/metadata.json")
    }

    private fun includeMode(config: String): String {
        require(config == "STRICT" || config == "OFF") { "unsupported metadata-validation config: $config" }
        return config
    }

    /**
     * DOC-CR5 security coherence : STRICT includeGuard + UNSAFE safeMode would be rejected
     * as a security illusion, so the STRICT config pairs with SERVER (same pattern as the
     * `STRICT_SERVER` config of the `@document-validation` scenarios, S-225).
     */
    private fun safeMode(config: String): String = when (config) {
        "STRICT" -> "SERVER"
        else -> "UNSAFE"
    }

    @When("the collectDocumentRetrieve task runs after validateDocument")
    fun `collectDocumentRetrieve runs after validateDocument`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument", "collectDocumentRetrieve")
            .withPluginClasspath()
            .forwardOutput()
            .build()
    }

    @When("the validateDocument step runs and fails its STRICT build")
    fun `validateDocument step runs and fails its STRICT build`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
    }

    @When("the collectDocumentRetrieve task runs alone")
    fun `collectDocumentRetrieve task runs alone`() {
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("collectDocumentRetrieve")
            .withPluginClasspath()
            .forwardOutput()
            .build()
    }

    private fun overallStatus(): String =
        DocumentValidationReport.fromJson(
            projectDir.resolve("build/docs/document/document-validation-report.json").readText(),
        ).overallStatus()

    @Then("the metadata.json carries validationStatus PASS")
    fun `metadata carries validationStatus PASS`() {
        assertTrue(metadataFile.exists(), "metadata.json must be produced")
        val content = metadataFile.readText()
        assertTrue(
            content.contains("\"validationStatus\" : \"PASS\""),
            "metadata.json must mark validationStatus PASS (report status: ${runCatching { overallStatus() }}) — actual: $content",
        )
    }

    @Then("the metadata.json carries validationStatus FAIL")
    fun `metadata carries validationStatus FAIL`() {
        assertTrue(metadataFile.exists(), "metadata.json must be produced by the second run")
        val content = metadataFile.readText()
        assertTrue(
            content.contains("\"validationStatus\" : \"FAIL\""),
            "metadata.json must mark validationStatus FAIL — actual: $content",
        )
    }

    @Then("the metadata.json omits validationStatus")
    fun `metadata omits validationStatus`() {
        assertTrue(metadataFile.exists(), "metadata.json must still be produced")
        val content = metadataFile.readText()
        assertFalse(content.contains("validationStatus"), "metadata.json must omit validationStatus — actual: $content")
    }
}