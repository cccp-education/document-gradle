package document.bookpublish

import document.BookLanguageTarget
import document.BookPublicationPlanner
import document.BookPublicationTarget
import document.DocumentFormat
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-publish` scenarios (EPIC DOC-BOOK-PUBLISH,
 * US-3).
 *
 * Pure BDD: the steps exercise the [BookPublicationPlanner] domain directly —
 * no Gradle task, no I/O. All step texts carry the `book-publish ` prefix
 * (anti-glue-collision pattern, S-088/S-223).
 */
class BookPublishSteps {

    private var languages: List<BookLanguageTarget> = emptyList()
    private var formats: List<String> = emptyList()
    private var baseName: String = "book"
    private var plan: List<BookPublicationTarget> = emptyList()

    private fun quoted(text: String): List<String> =
        Regex("\"([^\"]*)\"").findAll(text).map { it.groupValues[1] }.toList()

    @Given("^book-publish the languages (.+)$")
    fun `book-publish the languages`(listText: String) {
        languages = quoted(listText).map {
            BookLanguageTarget(code = it, name = it, nativeName = it, rtl = false)
        }
    }

    @Given("^book-publish the requested formats (.+)$")
    fun `book-publish the requested formats`(listText: String) {
        formats = quoted(listText)
    }

    @Given("^book-publish a base name \"([^\"]*)\"$")
    fun `book-publish a base name`(base: String) {
        baseName = base
    }

    @When("^book-publish the publication plan is resolved$")
    fun `book-publish the publication plan is resolved`() {
        plan = BookPublicationPlanner.plan(languages, baseName, formats)
    }

    @Then("^book-publish the outputs are (.+)$")
    fun `book-publish the outputs are`(expectedText: String) {
        assertEquals(quoted(expectedText), plan.map { it.outputFileName }, "the output order must match")
    }

    @Then("^book-publish the publication plan is empty$")
    fun `book-publish the publication plan is empty`() {
        assertTrue(plan.isEmpty(), "the plan must be empty, got: ${plan.map { it.outputFileName }}")
    }

    @Then("^book-publish the source of \"([^\"]*)\" in \"([^\"]*)\" is \"([^\"]*)\"$")
    fun `book-publish the source of a language in a format`(lang: String, format: String, expected: String) {
        val target = plan.first { it.language == lang && it.format == DocumentFormat.valueOf(format.uppercase()) }
        assertEquals(expected, target.sourceFileName, "the source file name must follow the convention")
    }

    @Then("^book-publish the output of \"([^\"]*)\" in \"([^\"]*)\" is \"([^\"]*)\"$")
    fun `book-publish the output of a language in a format`(lang: String, format: String, expected: String) {
        val target = plan.first { it.language == lang && it.format == DocumentFormat.valueOf(format.uppercase()) }
        assertEquals(expected, target.outputFileName, "the output file name must follow the convention")
    }
}
