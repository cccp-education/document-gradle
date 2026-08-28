package document.bookvalidation

import document.BookSection
import document.BookValidationResult
import document.BookValidator
import document.ValidationMode
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.gradle.api.GradleException
import java.io.File

/**
 * Step definitions for the `@book-validation` scenarios (DOC-BOOK-VALIDATE-4).
 *
 * Pure BDD: the steps exercise [BookValidator] directly (file-level validation
 * against a TOC + the STRICT/LENIENT enforcement contract) — no Gradle task, no
 * external I/O beyond a temporary pages directory. All step texts carry the
 * `book-validation ` prefix (anti-glue-collision pattern).
 */
class BookValidationSteps {

    private lateinit var pagesDir: File
    private var toc: MutableList<BookSection> = mutableListOf()
    private var result: BookValidationResult? = null
    private var enforcedFailure: GradleException? = null

    @Before
    fun setup() {
        pagesDir = File.createTempFile("book-validation-", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }
        toc = mutableListOf()
        result = null
        enforcedFailure = null
    }

    @Given("book-validation a TOC with ref {string} at page {int}")
    fun `a TOC with ref at page`(ref: String, page: Int) {
        toc.add(
            BookSection(
                ref = ref,
                title = "Section $ref",
                page = page,
                pdfFile = "%03d.adoc".format(page),
            ),
        )
    }

    @Given("book-validation an OCR page {string} with content {string}")
    fun `an OCR page with content`(fileName: String, content: String) {
        File(pagesDir, fileName).writeText(content)
    }

    @Given("book-validation no OCR page for page {int}")
    fun `no OCR page for page`(page: Int) {
        // intentionally do not create any file whose leading digits equal [page]
    }

    @When("book-validation the book is validated")
    fun `the book is validated`() {
        result = BookValidator.validate(pagesDir = pagesDir, toc = toc, pdfsDir = null)
    }

    @When("book-validation the book is validated in {string} mode")
    fun `the book is validated in mode`(mode: String) {
        result = BookValidator.validate(pagesDir = pagesDir, toc = toc, pdfsDir = null)
        val reasons = (result as? BookValidationResult.Invalid)?.reasons ?: emptyList()
        val validationMode = ValidationMode.valueOf(mode)
        try {
            BookValidator.enforce(validationMode, reasons)
        } catch (e: GradleException) {
            enforcedFailure = e
        }
    }

    @Then("book-validation the result is valid")
    fun `the result is valid`() {
        assertTrue(result is BookValidationResult.Valid, "expected valid result, got: $result")
    }

    @Then("book-validation the result is invalid")
    fun `the result is invalid`() {
        assertTrue(result is BookValidationResult.Invalid, "expected invalid result, got: $result")
    }

    @Then("book-validation the result has {int} reason(s)")
    fun `the result has reasons`(count: Int) {
        val reasons = (result as? BookValidationResult.Invalid)?.reasons ?: emptyList()
        assertEquals(count, reasons.size, "reasons: $reasons")
    }

    @Then("book-validation reason {int} mentions {string}")
    fun `reason mentions`(index: Int, snippet: String) {
        val reasons = (result as? BookValidationResult.Invalid)?.reasons ?: emptyList()
        val actual = reasons.getOrElse(index - 1) { "MISSING" }
        assertTrue(actual.contains(snippet), "reason $index: '$actual' does not mention '$snippet'")
    }

    @Then("book-validation a build failure is raised mentioning {string}")
    fun `a build failure is raised mentioning`(snippet: String) {
        val failure = enforcedFailure
        assertTrue(failure != null, "expected a build failure (GradleException) to be raised")
        assertTrue(
            failure!!.message?.contains(snippet) == true,
            "failure message '${failure.message}' does not mention '$snippet'",
        )
    }
}
