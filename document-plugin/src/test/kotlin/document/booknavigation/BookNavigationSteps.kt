package document.booknavigation

import document.BookAssembler
import document.BookLayout
import document.BookNumbering
import document.BookSection
import document.BookTreeBuilder
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-navigation` scenarios
 * (DOC-BOOK-CONSISTENCY-B6 — opt-in previous / next links, fixes S-258 B6).
 *
 * Pure BDD: the steps exercise the navigation domain objects directly
 * ([BookNumbering], [BookLayout], [BookAssembler]) — no Gradle task, no
 * external I/O. All step texts carry the `book-navigation ` prefix
 * (anti-glue-collision pattern).
 */
class BookNavigationSteps {

    private var sections: List<BookSection> = emptyList()
    private var assembled: String = ""

    @Given("^book-navigation a TOC with refs (.+)$")
    fun `book-navigation a TOC with refs`(refsText: String) {
        val refs = Regex("\"([^\"]+)\"").findAll(refsText).map { it.groupValues[1] }.toList()
        require(refs.isNotEmpty()) { "at least one ref must be given, got: '$refsText'" }
        sections = refs.mapIndexed { index, ref ->
            BookSection(ref = ref, title = "Section $ref", page = index + 1, pdfFile = "%03d.adoc".format(index + 1))
        }
    }

    @Given("book-navigation OCR pages for every referenced section")
    fun `book-navigation OCR pages for every referenced section`() {
        require(sections.isNotEmpty()) { "a TOC must be given before the OCR pages" }
    }

    @Given("^book-navigation a multi-page section \"([^\"]+)\" spanning pages (\\d+) and (\\d+)$")
    fun `book-navigation a multi-page section spanning pages`(ref: String, first: Int, second: Int) {
        val section = sections.first { it.ref == ref }
        // Expand the logical section into two physical pages sharing the same ref
        // (the `| 1.1 | … | 2, 3 | …` TOC convention handled by BookTocParser).
        val expanded = sections.flatMap { current ->
            if (current.ref == ref) {
                listOf(
                    current.copy(title = section.title, page = first, pdfFile = "%03d.adoc".format(first)),
                    current.copy(title = section.title, page = second, pdfFile = "%03d.adoc".format(second)),
                )
            } else {
                listOf(current)
            }
        }
        sections = expanded
    }

    @When("book-navigation the book is assembled with navigation")
    fun `book-navigation the book is assembled with navigation`() {
        assembled = assemble(emitNavigation = true)
    }

    @When("book-navigation the book is assembled without navigation")
    fun `book-navigation the book is assembled without navigation`() {
        assembled = assemble(emitNavigation = false)
    }

    @Then("^book-navigation section \"([^\"]+)\" links back to \"([^\"]+)\"$")
    fun `book-navigation section links back to`(ref: String, previous: String) {
        assertTrue(
            navigationLine(ref).contains(pattern(previous)),
            "section '$ref' must link back to '$previous', got:\n$assembled",
        )
    }

    @Then("^book-navigation section \"([^\"]+)\" links forward to \"([^\"]+)\"$")
    fun `book-navigation section links forward to`(ref: String, next: String) {
        assertTrue(
            navigationLine(ref).contains(pattern(next)),
            "section '$ref' must link forward to '$next', got:\n$assembled",
        )
    }

    @Then("^book-navigation section \"([^\"]+)\" has no previous link$")
    fun `book-navigation section has no previous link`(ref: String) {
        val line = navigationLine(ref)
        assertFalse(
            line.contains(pattern(before(ref))),
            "section '$ref' must have no previous link, got: '$line'",
        )
    }

    @Then("^book-navigation section \"([^\"]+)\" has no next link$")
    fun `book-navigation section has no next link`(ref: String) {
        val line = navigationLine(ref)
        assertFalse(
            line.contains(pattern(after(ref))),
            "section '$ref' must have no next link, got: '$line'",
        )
    }

    @Then("book-navigation the book carries no navigation link")
    fun `book-navigation the book carries no navigation link`() {
        assertFalse(
            Regex("""<<[^>]+,Section [^>]+>>""").containsMatchIn(assembled),
            "no navigation link must be emitted, got:\n$assembled",
        )
    }

    private fun assemble(emitNavigation: Boolean): String {
        val tree = BookTreeBuilder.fromSections(sections)
        return BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(emitNavigation = emitNavigation, pageBreakBetweenNodes = false),
            title = "Navigation Book",
            author = "Cheroliv",
            resolveContent = { section -> "Body of ${section.ref}" },
        ).content
    }

    /** The navigation line emitted directly after [ref]'s section block. */
    private fun navigationLine(ref: String): String {
        val start = assembled.indexOf("[[$ref]]")
        require(start >= 0) { "section '$ref' must be emitted, got:\n$assembled" }
        val end = assembled.indexOf("[[", start + 1).let { if (it < 0) assembled.length else it }
        return assembled.substring(start, end)
    }

    private fun pattern(ref: String): String = "<<" + ref + ",Section " + ref + ">>"

    private fun before(ref: String): String {
        val refs = sections.map { it.ref }.distinct()
        val idx = refs.indexOf(ref)
        return if (idx > 0) refs[idx - 1] else ref
    }

    private fun after(ref: String): String {
        val refs = sections.map { it.ref }.distinct()
        val idx = refs.indexOf(ref)
        return if (idx < refs.lastIndex) refs[idx + 1] else ref
    }
}
