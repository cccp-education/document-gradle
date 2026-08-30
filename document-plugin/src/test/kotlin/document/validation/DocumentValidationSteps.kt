package document.validation

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@document-validation` scenarios (DOC-VALIDATE-COMPOSITE).
 *
 * BDD with a real Gradle build (TestKit) : the steps write a build script that applies the
 * document plugin with a `converter { }` block derived from the given config keyword, run
 * `validateDocument`, and assert the consolidated outcome (skip / warn / reject / report).
 * Every step text carries the `document-validation` prefix (anti-glue pattern from S-223).
 */
class DocumentValidationSteps {

    private lateinit var projectDir: File
    private lateinit var buildOutput: String
    private lateinit var reportFile: File

    @Given("a document gradle project with document-validation config {string} and source {string}")
    fun `a project with document-validation config and source`(config: String, source: String) {
        projectDir = Files.createTempDirectory("doc-validation-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-validation\"\n")
        projectDir.resolve("build.gradle.kts").writeText(buildScript(config))
        projectDir.resolve("doc.adoc").writeText(source)
        reportFile = projectDir.resolve("build/docs/document/document-validation-report.json")
    }

    private fun buildScript(config: String): String {
        val block = when (config) {
            "STRICT" ->
                "converter { includeGuard = IncludeGuardMode.STRICT; xrefValidation = XrefValidationMode.STRICT; safeMode = SafeMode.UNSAFE }"
            "STRICT_SERVER" ->
                "converter { includeGuard = IncludeGuardMode.STRICT; xrefValidation = XrefValidationMode.STRICT; safeMode = SafeMode.SERVER }"
            "STRICT_XREF" ->
                "converter { xrefValidation = XrefValidationMode.STRICT }"
            "LENIENT" ->
                "converter { includeGuard = IncludeGuardMode.LENIENT; xrefValidation = XrefValidationMode.LENIENT; safeMode = SafeMode.UNSAFE }"
            "OFF" ->
                "converter { includeGuard = IncludeGuardMode.OFF; xrefValidation = XrefValidationMode.OFF; safeMode = SafeMode.UNSAFE }"
            else -> "converter { }"
        }
        return """
            import document.security.IncludeGuardMode
            import document.xref.XrefValidationMode
            import org.asciidoctor.SafeMode

            plugins {
                id("education.cccp.document")
            }

            document {
                source.set(file("doc.adoc"))
                $block
            }
            """.trimIndent()
    }

    @When("the validateDocument task runs successfully")
    fun `validateDocument runs successfully`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocument task runs and fails")
    fun `validateDocument runs and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("validateDocument")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    @Then("the document-validation-report.json marks VALID everywhere")
    fun `report marks VALID everywhere`() {
        assertTrue(reportFile.exists(), "le rapport consolidé doit être écrit")
        val json = reportFile.readText()
        assertTrue(json.contains("\"advice\" : \"VALID\"") || json.contains("\"advice\":\"VALID\""), "security must be VALID")
        assertFalse(json.contains("INVALID"), "a VALID report must not contain any INVALID block")
        assertFalse(json.contains("MISSING"), "a VALID report must not contain any MISSING block")
    }

    @Then("the document-validation-report.json marks include INVALID")
    fun `report marks include INVALID`() {
        assertTrue(reportFile.exists(), "le rapport consolidé doit être écrit")
        assertTrue(reportFile.readText().contains("INVALID"), "le rapport doit marquer l'include INVALID")
    }

    @Then("the document-validation-report.json marks security REJECT")
    fun `report marks security REJECT`() {
        assertTrue(reportFile.readText().contains("REJECT"), "le rapport doit marquer la security REJECT")
    }

    @Then("the document-validation-report.json lists the missing reference {string}")
    fun `report lists missing reference`(ref: String) {
        assertTrue(reportFile.exists(), "le rapport consolidé doit être écrit même en STRICT")
        val json = reportFile.readText()
        assertTrue(json.contains("MISSING") && json.contains(ref), "le rapport doit lister '$ref'")
    }

    @Then("the document-validation-report.json marks xref MISSING")
    fun `report marks xref MISSING`() {
        assertTrue(reportFile.readText().contains("MISSING"), "le rapport doit marquer le xref MISSING")
    }

    @Then("the document-validation-report.json marks security WARN")
    fun `report marks security WARN`() {
        assertTrue(reportFile.readText().contains("WARN"), "le rapport doit marquer la security WARN")
    }

    @Then("the build fails with document-validation message {string}")
    fun `build fails with document-validation message`(message: String) {
        assertTrue(
            buildOutput.contains(message, ignoreCase = true),
            "la validation STRICT doit rejeter (sortie: $buildOutput)",
        )
    }

    @Then("the document-validation-report.json contains no INVALID or MISSING block")
    fun `report contains no INVALID or MISSING`() {
        val json = reportFile.readText()
        assertFalse(json.contains("INVALID"), "en OFF l'include n'est pas audité")
        assertFalse(json.contains("MISSING"), "en OFF le xref n'est pas audité")
    }
}
