package document.htmllint

import document.validation.HtmlLinkLintMode
import document.validation.HtmlLinkLintReport
import document.validation.HtmlLinkLinter
import document.validation.HtmlLinkLintResult
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@html-link-lint` scenarios (EPIC DOC-HTML-LINT).
 *
 * BDD over the pure DDD [HtmlLinkLinter] (mirrors [document.xrefvalidation]
 * but without a Gradle task : the linter is a pure function, so the steps
 * drive it directly). All step texts carry the `htmllint` prefix (anti-glue
 * pattern from S-223). The `OFF`/`LENIENT`/`STRICT` mode handling mirrors
 * how a future `lintHtmlDocument` task would surface findings.
 */
class HtmlLinkLintSteps {

    private lateinit var html: String
    private lateinit var mode: HtmlLinkLintMode
    private var outcome: String = "none"
    private lateinit var reportFile: File
    private var thrown: Throwable? = null

    @Given("a htmllint html document with mode {string} and content {string}")
    fun `a document with mode and content`(mode: String, content: String) {
        this.mode = HtmlLinkLintMode.valueOf(mode)
        this.html = content
        this.reportFile = Files.createTempDirectory("doc-htmllint-").toFile()
            .resolve("html-link-lint-report.json")
    }

    @When("the htmllint validation runs")
    fun `the validation runs`() {
        val result = HtmlLinkLinter.validate(html)
        outcome = when (mode) {
            HtmlLinkLintMode.OFF -> "skip"
            HtmlLinkLintMode.LENIENT -> if (result is HtmlLinkLintResult.Invalid) "warn" else "valid"
            HtmlLinkLintMode.STRICT -> if (result is HtmlLinkLintResult.Invalid) "reject" else "valid"
        }
        if (outcome != "skip") {
            val report = HtmlLinkLintReport.fromResult(result, HtmlLinkLinter.hasTableOfContents(html))
            reportFile.writeText(report.toJson())
        }
        if (outcome == "reject") {
            thrown = IllegalStateException("html link lint (STRICT) found dead links")
        }
    }

    @Then("the htmllint outcome is {string}")
    fun `the outcome is`(expected: String) {
        assertTrue(outcome == expected, "attendu '$expected' mais obtenu '$outcome'")
    }

    @Then("no htmllint report is written")
    fun `no report written`() {
        assertTrue(!reportFile.exists(), "en mode OFF aucun rapport n'est produit")
    }

    @Then("the htmllint report marks DEAD with {string}")
    fun `report marks DEAD`(ref: String) {
        assertTrue(reportFile.exists(), "le rapport JSON doit être écrit")
        val json = reportFile.readText()
        assertTrue(json.contains("DEAD") && json.contains(ref), "le rapport doit lister '$ref'")
    }

    @Then("the htmllint build fails with rejection")
    fun `build fails with rejection`() {
        assertTrue(thrown != null, "le mode STRICT doit rejeter la validation")
    }
}
