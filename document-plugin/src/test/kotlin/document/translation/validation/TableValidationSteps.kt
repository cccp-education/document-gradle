package document.translation.validation

import document.translation.ArticleTranslator
import document.translation.AsciiDocParser
import document.translation.AsciiDocRenderer
import document.translation.BatchDocumentTranslator
import document.translation.BatchTranslationRequest
import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import document.translation.TranslationException
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import java.io.File
import kotlin.io.path.createTempDirectory

class TableValidationSteps {

    private var translationService: CorruptingTranslationService = CorruptingTranslationService()
    private var translator: DocumentTranslator? = null
    private var batchTranslator: BatchDocumentTranslator? = null
    private var validationMode: ValidationMode = ValidationMode.LENIENT
    private var asciidocSource: String = ""
    private var translatedOutput: String? = null
    private var thrownException: Exception? = null
    private var batchOutputDir: File? = null

    @Given("a document translator with validation mode {string}")
    fun `a document translator with validation mode`(mode: String) {
        validationMode = ValidationMode.valueOf(mode.uppercase())
        translationService = CorruptingTranslationService()
        translator = DocumentTranslator(
            translationService = translationService,
            tableValidationMode = validationMode,
        )
    }

    @And("the translation service corrupts {string} to {string}")
    fun `the translation service corrupts text`(sourceText: String, corruptedText: String) {
        translationService.corrupt(sourceText, corruptedText)
    }

    @Given("an AsciiDoc article with a valid table")
    fun `an asciidoc article with a valid table`() {
        asciidocSource = """
= Test Article

[cols="1,3"]
|===
|Option |Description

|`-c`, `--clean`
|Clean build directory
|===
""".trimIndent()
    }

    @Given("an AsciiDoc article with a table containing {string}")
    fun `an asciidoc article with a table containing`(text: String) {
        asciidocSource = """
= Test Article

[cols="1,3"]
|===
|Option |Description

|`-c`, `--clean`
|$text
|===
""".trimIndent()
    }

    @Given("an AsciiDoc article with a table having mismatched column counts")
    fun `an asciidoc article with a table having mismatched column counts`() {
        asciidocSource = """
= Test Article

|===
|A |B |C

|x |y
|===
""".trimIndent()
    }

    @Given("an AsciiDoc article with a table having cols {string} but 3 columns")
    fun `an asciidoc article with a table having cols but 3 columns`(cols: String) {
        asciidocSource = """
= Test Article

[cols="$cols"]
|===
|Option |Description |Extra

|`-c`, `--clean`
|Clean build directory
|Extra cell
|===
""".trimIndent()
    }

    @When("I translate the article from {string} to {string}")
    fun `i translate the article from source to target`(sourceLang: String, targetLang: String) {
        thrownException = null
        translatedOutput = null
        try {
            translatedOutput = translator!!.translate(asciidocSource, sourceLang, targetLang)
        } catch (e: Exception) {
            thrownException = e
        }
    }

    @Then("the translation should succeed")
    fun `the translation should succeed`() {
        assertNotNull(translatedOutput, "Translation should have produced output but threw: ${thrownException?.message}")
    }

    @And("no validation errors should be reported")
    fun `no validation errors should be reported`() {
        val results = translator!!.tableValidationResults
        assertTrue(results.isEmpty(), "Expected no validation errors but got: $results")
    }

    @And("{int} validation error should be reported")
    fun `validation error should be reported`(count: Int) {
        val results = translator!!.tableValidationResults
        assertEquals(count, results.size, "Expected $count validation error(s) but got ${results.size}: $results")
    }

    @And("the validation error should mention {string}")
    fun `the validation error should mention`(text: String) {
        val results = translator!!.tableValidationResults
        assertTrue(results.isNotEmpty(), "Expected at least one validation error")
        val found = results.any { it.reason.contains(text) }
        assertTrue(found, "Expected a validation error mentioning '$text' but got: ${results.map { it.reason }}")
    }

    @Then("a TranslationException should be thrown with message containing {string}")
    fun `a translationexception should be thrown with message containing`(text: String) {
        assertNotNull(thrownException, "Expected TranslationException but no exception was thrown")
        assertTrue(
            thrownException is TranslationException,
            "Expected TranslationException but got ${thrownException!!.javaClass.simpleName}",
        )
        assertTrue(
            thrownException!!.message!!.contains(text),
            "Expected message containing '$text' but got: ${thrownException!!.message}",
        )
    }

    @Given("a batch translator with {int} articles including {int} with a corrupted table")
    fun `a batch translator with articles including corrupted`(total: Int, corrupted: Int) {
        val tempDir = createTempDirectory("doc-table-validation-batch-")
        val sourceDir = tempDir.resolve("source").toFile()
        batchOutputDir = tempDir.resolve("output").toFile()
        sourceDir.mkdirs()
        batchOutputDir!!.mkdirs()

        val validArticle = """
= Article {n}

Some paragraph text.

[cols="1,3"]
|===
|Option |Description

|`-c`, `--clean`
|Clean build directory
|===

Another paragraph.
""".trimIndent()

        val corruptedArticle = """
= Corrupted Article

Some paragraph text.

[cols="1,3"]
|===
|Option |Description

|`-c`, `--clean`
|Clean |=== build directory
|===

Another paragraph.
""".trimIndent()

        for (i in 1..total) {
            val file = sourceDir.resolve("article-$i.adoc")
            if (i <= corrupted) {
                file.writeText(corruptedArticle.replace("{n}", "$i"))
            } else {
                file.writeText(validArticle.replace("{n}", "$i"))
            }
        }

        val docTranslator = DocumentTranslator(
            translationService = FakeTranslationService(" [EN]"),
            tableValidationMode = ValidationMode.LENIENT,
        )
        batchTranslator = BatchDocumentTranslator(docTranslator)
    }

    @When("I run batch translation from {string} to {string}")
    fun `i run batch translation from source to target`(sourceLang: String, targetLang: String) {
        val tempDir = batchOutputDir!!.parentFile
        val sourceDir = tempDir.resolve("source")
        val request = BatchTranslationRequest(
            sourceDir = sourceDir,
            outputDir = batchOutputDir!!,
            sourceLanguage = sourceLang,
            targetLanguage = targetLang,
        )
        batchTranslator!!.translateBatch(request)
    }

    @Then("a table-validation-report.json should be produced")
    fun `a table validation report json should be produced`() {
        val reportFile = batchOutputDir!!.resolve("table-validation-report.json")
        assertTrue(reportFile.isFile, "Expected table-validation-report.json at ${reportFile.absolutePath}")
    }

    @And("the report should contain {int} invalid entry")
    fun `the report should contain invalid entry`(count: Int) {
        val reportFile = batchOutputDir!!.resolve("table-validation-report.json")
        val content = reportFile.readText()
        val mapper = com.fasterxml.jackson.databind.ObjectMapper()
        val node = mapper.readTree(content)
        val entries = node.get("entries")
        assertNotNull(entries, "Expected 'entries' field in report JSON")
        assertEquals(count, entries.size(), "Expected $count invalid entry/entries but got ${entries.size()}: $content")
    }
}
