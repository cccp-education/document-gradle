package document.bookmultilang

import document.BookLanguagePlanner
import document.BookLanguageTarget
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-multilang` scenarios (EPIC
 * DOC-BOOK-MULTILANG, US-3).
 *
 * Pure BDD: the steps exercise the [BookLanguagePlanner] domain directly
 * against the real N0 `LanguageCatalog` — no Gradle task, no I/O. All step
 * texts carry the `book-multilang ` prefix (anti-glue-collision pattern,
 * S-088/S-223).
 */
class BookMultilangSteps {

    private var source: String = "fr"
    private var requested: List<String> = emptyList()
    private var plan: List<BookLanguageTarget> = emptyList()

    @Given("^book-multilang a source language \"([^\"]*)\"$")
    fun `book-multilang a source language`(language: String) {
        source = language
    }

    @Given("^book-multilang a requested subset (.+)$")
    fun `book-multilang a requested subset`(subsetText: String) {
        requested = Regex("\"([^\"]*)\"").findAll(subsetText).map { it.groupValues[1] }.toList()
    }

    @When("^book-multilang the plan is resolved without translateToAll$")
    fun `book-multilang the plan is resolved without translateToAll`() {
        plan = BookLanguagePlanner.plan(source, requested, translateToAll = false)
    }

    @When("^book-multilang the plan is resolved with translateToAll$")
    fun `book-multilang the plan is resolved with translateToAll`() {
        plan = BookLanguagePlanner.plan(source, requested, translateToAll = true)
    }

    @Then("^book-multilang the plan targets (.+?) in order$")
    fun `book-multilang the plan targets in order`(expectedText: String) {
        val expected = Regex("\"([^\"]*)\"").findAll(expectedText).map { it.groupValues[1] }.toList()
        assertEquals(expected, plan.map { it.code }, "the plan order must match")
    }

    @Then("^book-multilang the plan targets \"([^\"]*)\"$")
    fun `book-multilang the plan targets exactly one`(code: String) {
        assertEquals(listOf(code), plan.map { it.code }, "the plan must target exactly '$code'")
    }

    @Then("^book-multilang the plan contains \"([^\"]*)\" and \"([^\"]*)\"$")
    fun `book-multilang the plan contains both`(first: String, second: String) {
        val codes = plan.map { it.code }
        assertTrue(codes.contains(first) && codes.contains(second), "the plan must contain '$first' and '$second', got: $codes")
    }

    @Then("^book-multilang the plan excludes the source \"([^\"]*)\"$")
    fun `book-multilang the plan excludes the source`(code: String) {
        assertTrue(plan.none { it.code == code }, "the source language '$code' must be excluded, got: ${plan.map { it.code }}")
    }

    @Then("^book-multilang the plan is empty$")
    fun `book-multilang the plan is empty`() {
        assertTrue(plan.isEmpty(), "no knob must yield an empty plan, got: ${plan.map { it.code }}")
    }

    @Then("^book-multilang target \"([^\"]*)\" maps to \"([^\"]*)\" for base \"([^\"]*)\"$")
    fun `book-multilang target maps to a file name`(code: String, expected: String, base: String) {
        val target = plan.first { it.code == code }
        assertEquals(expected, target.fileName(base), "the file name must follow the convention")
    }

    @Then("^book-multilang target \"([^\"]*)\" is right-to-left$")
    fun `book-multilang target is right-to-left`(code: String) {
        val target = plan.first { it.code == code }
        assertTrue(target.rtl, "target '$code' must carry the RTL flag")
    }
}
