package document.booktree

import document.BookAssembler
import document.BookLayout
import document.BookNumbering
import document.BookSection
import document.BookTreeBuilder
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-tree` scenarios (DOC-BOOK-DOMAIN-5).
 *
 * Pure BDD: the steps exercise the "Book" domain objects directly
 * ([BookTreeBuilder], [BookAssembler], [BookNumbering]) — no Gradle task, no
 * external I/O beyond the flat-blob scenario's temporary pages directory.
 * All step texts carry the `book-tree ` prefix (anti-glue-collision pattern).
 */
class BookTreeSteps {

    private val title = "Structured Book"
    private val author = "Cheroliv"

    private var sections: List<BookSection> = emptyList()
    private var pageContent: Map<Int, String> = emptyMap()
    private var assembled: String? = null
    private var navigationRefs: Pair<String?, String?> = null to null
    private var flatPagesDir: java.io.File? = null

    @Given("^book-tree a TOC with refs (.+)$")
    fun `book-tree a TOC with refs`(refsText: String) {
        val refs = Regex("\"([^\"]+)\"").findAll(refsText).map { it.groupValues[1] }.toList()
        require(refs.isNotEmpty()) { "at least one ref must be given, got: '$refsText'" }
        sections = refs.mapIndexed { index, ref ->
            BookSection(
                ref = ref,
                title = "Section $ref",
                page = index + 1,
                pdfFile = "%03d.adoc".format(index + 1),
            )
        }
        pageContent = sections.associate { it.page to "Content of ${it.ref}" }
    }

    @Given("book-tree a TOC with front, body and back matter sections")
    fun `book-tree a TOC with front body and back matter sections`() {
        `book-tree a TOC with refs`("\"0.1\", \"1\", \"1.1\" and \"9.1\"")
    }

    @Given("book-tree OCR pages for every referenced section")
    fun `book-tree OCR pages for every referenced section`() {
        require(sections.isNotEmpty()) { "a TOC must be given before the OCR pages" }
    }

    @When("book-tree the book is assembled from the tree")
    fun `book-tree the book is assembled from the tree`() {
        val tree = BookTreeBuilder.fromSections(sections)
        assembled = BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(),
            title = title,
            author = author,
            resolveContent = { section -> pageContent[section.page] ?: "" },
        ).content
    }

    @Then("book-tree the title page is emitted with author {string}")
    fun `book-tree the title page is emitted with author`(expectedAuthor: String) {
        val content = assembled.orEmpty()
        assertTrue(content.startsWith("= $title"), "title page must start the book, got: ${content.take(80)}")
        assertTrue(content.contains(":author: $expectedAuthor"), "author attribute must be present")
    }

    @Then("book-tree section {string} is emitted as a level-1 heading")
    fun `book-tree section is emitted as a level-1 heading`(ref: String) {
        val content = assembled.orEmpty()
        assertTrue(
            content.lines().any { it.startsWith("== ") && it.drop(3).startsWith("$ref.") },
            "section '$ref' must be emitted as a level-1 (==) heading, got:\n$content",
        )
    }

    @Then("book-tree section {string} is emitted as a level-2 heading")
    fun `book-tree section is emitted as a level-2 heading`(ref: String) {
        val content = assembled.orEmpty()
        assertTrue(
            content.lines().any { it.startsWith("=== ") && it.drop(4).startsWith("$ref.") },
            "section '$ref' must be emitted as a level-2 (===) heading, got:\n$content",
        )
    }

    @Then("book-tree every emitted section carries its cross-reference anchor")
    fun `book-tree every emitted section carries its cross-reference anchor`() {
        val content = assembled.orEmpty()
        for (section in sections) {
            assertTrue(
                content.contains("[[${section.ref}]]"),
                "anchor of section '${section.ref}' must be emitted",
            )
        }
    }

    @Then("book-tree the front matter precedes the body")
    fun `book-tree the front matter precedes the body`() {
        val content = assembled.orEmpty()
        val front = content.indexOf("[[0.1]]")
        val body = content.indexOf("[[1]]")
        assertTrue(front in 0 until body, "front matter [[0.1]] must precede body [[1]], got: front=$front body=$body")
    }

    @Then("book-tree the body precedes the back matter")
    fun `book-tree the body precedes the back matter`() {
        val content = assembled.orEmpty()
        val body = content.indexOf("[[1]]")
        val back = content.indexOf("[[9.1]]")
        assertTrue(body in 0 until back, "body [[1]] must precede back matter [[9.1]], got: body=$body back=$back")
    }

    @Then("book-tree the heading of section {string} starts with {string}")
    fun `book-tree the heading of section starts with`(ref: String, prefix: String) {
        val content = assembled.orEmpty()
        val heading = content.lines().firstOrNull { line ->
            line.startsWith("=") &&
                line.dropWhile { it == '=' }.dropWhile { it == ' ' }.startsWith(prefix)
        }
        assertTrue(
            heading != null,
            "a heading starting with '$prefix' must be emitted for section '$ref', got:\n$content",
        )
    }

    @When("book-tree the navigation of section {string} is computed")
    fun `book-tree the navigation of section is computed`(ref: String) {
        val tree = BookTreeBuilder.fromSections(sections)
        val navigation = BookNumbering.navigation(tree, ref)
        navigationRefs = navigation.previous?.ref to navigation.next?.ref
    }

    @Then("book-tree the previous section is {string}")
    fun `book-tree the previous section is`(ref: String) {
        assertEquals(ref, navigationRefs.first, "previous section mismatch")
    }

    @Then("book-tree the next section is {string}")
    fun `book-tree the next section is`(ref: String) {
        assertEquals(ref, navigationRefs.second, "next section mismatch")
    }

    @Then("book-tree the previous section is absent")
    fun `book-tree the previous section is absent`() {
        assertNull(navigationRefs.first, "no previous section must exist")
    }

    @Then("book-tree the next section is absent")
    fun `book-tree the next section is absent`() {
        assertNull(navigationRefs.second, "no next section must exist")
    }

    @Given("book-tree OCR pages without any TOC")
    fun `book-tree OCR pages without any TOC`() {
        val dir = Files.createTempDirectory("book-tree-flat").toFile().apply { deleteOnExit() }
        java.io.File(dir, "001-first.adoc").writeText("Flat page one text.")
        java.io.File(dir, "002-second.adoc").writeText("Flat page two text.")
        java.io.File(dir, "003-third.adoc").writeText("Flat page three text.")
        flatPagesDir = dir
    }

    @When("book-tree the book is assembled without a tree")
    fun `book-tree the book is assembled without a tree`() {
        assembled = BookAssembler.assemble(
            pagesDir = flatPagesDir!!,
            title = title,
            author = author,
        ).content
    }

    @Then("book-tree no structured hierarchical heading is emitted")
    fun `book-tree no structured hierarchical heading is emitted`() {
        val content = assembled.orEmpty()
        assertFalse(
            content.lines().any { it.startsWith("== ") || it.startsWith("=== ") },
            "no hierarchical heading must be emitted for a flat assembly, got:\n$content",
        )
        assertFalse(content.contains("[["), "no cross-reference anchor must be emitted for a flat assembly")
    }

    @Then("book-tree every page content is concatenated in page order")
    fun `book-tree every page content is concatenated in page order`() {
        val content = assembled.orEmpty()
        val first = content.indexOf("Flat page one text.")
        val second = content.indexOf("Flat page two text.")
        val third = content.indexOf("Flat page three text.")
        assertTrue(first >= 0 && second >= 0 && third >= 0, "every page must be present")
        assertTrue(first < second && second < third, "pages must be concatenated in page order")
    }
}
