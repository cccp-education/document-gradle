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

    /**
     * Fourth guard scenarios (DOC-VALIDATE-HTML-LINT) : distinct step text (anti-glue
     * pattern S-223) driving the `converter { htmlLinkLint = ... }` knob, then two tasks —
     * the HTML conversion (producer of the rendered output) and the composite validation.
     */
    @Given("a document gradle project with document-validationHtml config {string} and source {string}")
    fun `a project with document-validationHtml config and source`(config: String, source: String) {
        projectDir = Files.createTempDirectory("doc-validation-html-").toFile()
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doc-validation-html\"\n")
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
            "STRICT_HTML" ->
                "converter { htmlLinkLint = HtmlLinkLintMode.STRICT; safeMode = SafeMode.SERVER }"
            "LENIENT_HTML" ->
                "converter { htmlLinkLint = HtmlLinkLintMode.LENIENT; safeMode = SafeMode.SERVER }"
            // S-235 (Option B) — custom output name : same STRICT knob, the audited
            // HTML is the *real* custom-named output build/docs/document/custom.html.
            "CUSTOM_NAME" ->
                "converter { htmlLinkLint = HtmlLinkLintMode.STRICT; safeMode = SafeMode.SERVER }"
            else -> "converter { }"
        }
        return """
            import document.security.IncludeGuardMode
            import document.validation.HtmlLinkLintMode
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

    @When("the validateDocument task runs with the HTML conversion")
    fun `validateDocument runs with the HTML conversion`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "validateDocument")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocument task runs with the HTML conversion and fails")
    fun `validateDocument runs with the HTML conversion and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "validateDocument")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
    }

    /** S-235 (Option B, anti-glue S-223 distinct step texts) — same two-task chain as
     *  the plain HTML-lint scenarios, but the `outputFileName` knob renames the real
     *  conversion output (`custom.html`), which the fourth guard must audit. */
    @When("the validateDocument task runs with the custom output name")
    fun `validateDocument runs with the custom output name`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "validateDocument", "-Pdocument.outputFileName=custom")
            .withPluginClasspath()
            .forwardOutput()
            .build()
            .output
    }

    @When("the validateDocument task runs with the custom output name and fails")
    fun `validateDocument runs with the custom output name and fails`() {
        buildOutput = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("convertDocumentToHtml", "validateDocument", "-Pdocument.outputFileName=custom")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()
            .output
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

    @Then("the document-validation-report.json marks htmlLint DEAD listing {string}")
    fun `report marks htmlLint DEAD listing`(fragment: String) {
        assertTrue(reportFile.exists(), "le rapport consolidé doit être écrit")
        val json = reportFile.readText()
        assertTrue(json.contains("\"status\" : \"DEAD\""), "le lint HTML doit être DEAD (json: $json)")
        assertTrue(json.contains(fragment), "le fragment mort '$fragment' doit être listé")
        assertTrue(json.contains("htmlLint"), "le rapport doit porter le bloc 4e garde htmlLint")
    }

    @Then("the document-validation-report.json marks htmlLint VALID")
    fun `report marks htmlLint VALID`() {
        assertTrue(reportFile.exists(), "le rapport consolidé doit être écrit")
        val json = reportFile.readText()
        assertTrue(json.contains("htmlLint"), "le rapport doit porter le bloc 4e garde htmlLint")
        assertTrue(json.contains("\"status\" : \"VALID\""), "le lint HTML doit être VALID (json: $json)")
        assertFalse(json.contains("DEAD"), "aucun lien mort attendu (json: $json)")
    }

    /** S-235 (Option B) — the audited HTML is the *real* custom-named output: the
     *  static-path failure finding `<html-file-missing>` must never appear. */
    @Then("the document-validation-report.json does not report html-file-missing")
    fun `report does not report html file missing`() {
        val json = reportFile.readText()
        assertFalse(
            json.contains("html-file-missing"),
            "la 4e garde doit auditer le vrai HTML custom, pas le chemin statique (json: $json)",
        )
    }
}
