package document.bookmatter

import document.BookAssembler
import document.BookLayout
import document.BookSection
import document.BookTreeBuilder
import document.BookValidationResult
import document.BookValidator
import document.Matter
import document.MatterPolicy
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-matter` scenarios (EPIC DOC-BOOK-MATTER,
 * code-review S-258 B3).
 *
 * Pure BDD: the steps exercise the matter domain objects directly
 * ([MatterPolicy], [BookValidator.validateStructure], [BookAssembler]) — no
 * Gradle task, no external I/O. All step texts carry the `book-matter `
 * prefix (anti-glue-collision pattern).
 */
class BookMatterSteps {

    private var sections: List<BookSection> = emptyList()
    private var policy: MatterPolicy? = null
    private var validation: BookValidationResult? = null
    private var assembled: String = ""

    @Given("^book-matter a TOC with refs (.+)$")
    fun `book-matter a TOC with refs`(refsText: String) {
        val refs = Regex("\"([^\"]+)\"").findAll(refsText).map { it.groupValues[1] }.toList()
        require(refs.isNotEmpty()) { "at least one ref must be given, got: '$refsText'" }
        sections = refs.mapIndexed { index, ref ->
            BookSection(ref = ref, title = "Section $ref", page = index + 1, pdfFile = "%03d.adoc".format(index + 1))
        }
    }

    @Given("book-matter OCR pages for every referenced section")
    fun `book-matter OCR pages for every referenced section`() {
        require(sections.isNotEmpty()) { "a TOC must be given before the OCR pages" }
    }

    @When("book-matter the policy is derived from the TOC")
    fun `book-matter the policy is derived from the TOC`() {
        policy = MatterPolicy.derive(sections)
    }

    @When("book-matter the structure is validated with the derived policy")
    fun `book-matter the structure is validated with the derived policy`() {
        validation = BookValidator.validateStructure(sections, matterPolicy = MatterPolicy.derive(sections))
    }

    @When("book-matter the structure is validated with the legacy policy")
    fun `book-matter the structure is validated with the legacy policy`() {
        validation = BookValidator.validateStructure(sections, matterPolicy = MatterPolicy.DEFAULT)
    }

    @When("book-matter the structure is validated with the NONE policy")
    fun `book-matter the structure is validated with the NONE policy`() {
        validation = BookValidator.validateStructure(sections, matterPolicy = MatterPolicy.NONE)
    }

    @When("book-matter the book is assembled with matter breaks")
    fun `book-matter the book is assembled with matter breaks`() {
        assembled = assemble(emitMatterBreaks = true)
    }

    @When("book-matter the book is assembled without matter breaks")
    fun `book-matter the book is assembled without matter breaks`() {
        assembled = assemble(emitMatterBreaks = false)
    }

    @Then("book-matter the policy requires FRONT")
    fun `book-matter the policy requires FRONT`() {
        assertTrue(policy!!.requiresFront, "the policy must require FRONT")
    }

    @Then("book-matter the policy requires BACK")
    fun `book-matter the policy requires BACK`() {
        assertTrue(policy!!.requiresBack, "the policy must require BACK")
    }

    @Then("book-matter the policy requires no FRONT")
    fun `book-matter the policy requires no FRONT`() {
        assertFalse(policy!!.requiresFront, "the policy must not require FRONT")
    }

    @Then("book-matter the policy requires no BACK")
    fun `book-matter the policy requires no BACK`() {
        assertFalse(policy!!.requiresBack, "the policy must not require BACK")
    }

    @Then("book-matter ref {string} is classified as FRONT")
    fun `book-matter ref is classified as FRONT`(ref: String) {
        assertEquals(Matter.FRONT, policy!!.classify(ref), "ref '$ref' must be FRONT")
    }

    @Then("book-matter ref {string} is classified as BACK")
    fun `book-matter ref is classified as BACK`(ref: String) {
        assertEquals(Matter.BACK, policy!!.classify(ref), "ref '$ref' must be BACK")
    }

    @Then("book-matter ref {string} is classified as BODY")
    fun `book-matter ref is classified as BODY`(ref: String) {
        assertEquals(Matter.BODY, policy!!.classify(ref), "ref '$ref' must be BODY")
    }

    @Then("book-matter the structure is valid")
    fun `book-matter the structure is valid`() {
        assertTrue(validation is BookValidationResult.Valid, "expected a valid structure, got: $validation")
    }

    @Then("book-matter the structure is invalid")
    fun `book-matter the structure is invalid`() {
        assertTrue(validation is BookValidationResult.Invalid, "expected an invalid structure, got: $validation")
    }

    @Then("book-matter the findings mention {string}")
    fun `book-matter the findings mention`(snippet: String) {
        val reasons = (validation as? BookValidationResult.Invalid)?.reasons ?: emptyList()
        assertTrue(
            reasons.any { it.contains(snippet) },
            "expected a finding mentioning '$snippet', got: $reasons",
        )
    }

    @Then("book-matter the book carries a break between FRONT and BODY")
    fun `book-matter the book carries a break between FRONT and BODY`() {
        val front = assembled.indexOf("[[0.1]]")
        val body = assembled.indexOf("[[1]]")
        val between = assembled.substring(front, body)
        assertTrue("<<<" in between, "a break must separate FRONT and BODY, got:\n$assembled")
    }

    @Then("book-matter the book carries a break between BODY and BACK")
    fun `book-matter the book carries a break between BODY and BACK`() {
        val body = assembled.indexOf("[[1]]")
        val back = assembled.indexOf("[[9.1]]")
        val between = assembled.substring(body, back)
        assertTrue("<<<" in between, "a break must separate BODY and BACK, got:\n$assembled")
    }

    @Then("book-matter the book carries no matter break")
    fun `book-matter the book carries no matter break`() {
        assertFalse(
            Regex("(?m)^<<<$").containsMatchIn(assembled),
            "no matter break must be emitted, got:\n$assembled",
        )
    }

    private fun assemble(emitMatterBreaks: Boolean): String {
        val tree = BookTreeBuilder.fromSections(sections)
        val layout = BookLayout(
            emitMatterBreaks = emitMatterBreaks,
            // isolate the matter-transition behaviour: the level-based page
            // break would otherwise fire at the same boundaries.
            pageBreakBetweenNodes = false,
            matterPolicy = MatterPolicy.derive(sections),
        )
        return BookAssembler.assemble(
            tree = tree,
            layout = layout,
            title = "Matter Book",
            author = "Cheroliv",
            resolveContent = { section -> "Body of ${section.ref}" },
        ).content
    }
}
