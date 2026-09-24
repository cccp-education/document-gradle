package document.crossborough

import document.BookAssembler
import document.BookLayout
import document.BookTocParser
import document.BookTreeBuilder
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import io.cucumber.datatable.DataTable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import java.io.File
import java.nio.file.Files

/**
 * Step definitions for the `@cross-borough @book-pipeline` scenarios — the
 * faithful N2 <-> N2 filesystem contract between codex (Brooklyn) and document
 * (New Orleans).
 *
 * Fidelity rules (EPIC CROSS-DOC-BOOK-FIDELITY, S-263):
 * - fixtures reproduce the *real* codex output: raw OCR text, **no** AsciiDoc
 *   header (`CollectOcrTask` writes `OcrResult.structuredText` verbatim; the
 *   title lives in the file name and the TOC);
 * - the TOC-driven scenarios exercise the *production* path
 *   `BookTocParser -> BookTreeBuilder -> BookAssembler.assemble(tree, ...)`
 *   with [BookAssembler.contentAwareResolver], so a heading is proven to be
 *   *derived from the TOC*, never fabricated from page content.
 *
 * All step texts are unambiguous with the rest of the glue (anti-collision).
 */
class CrossBoroughSteps {

    private lateinit var pagesDir: File
    private lateinit var tocDir: File
    private var tocFile: File? = null
    private var result: document.BookAssemblyResult? = null

    @Before
    fun setUp() {
        pagesDir = Files.createTempDirectory("cross-borough-book").toFile()
        // The TOC lives in a sibling directory: it must not be picked up as an
        // OCR page by the `.adoc` scan of `pagesDir`.
        tocDir = Files.createTempDirectory("cross-borough-toc").toFile()
        tocFile = null
        result = null
    }

    @After
    fun tearDown() {
        pagesDir.deleteRecursively()
        tocDir.deleteRecursively()
    }

    @Given("a temporary pages directory simulating codex output")
    fun aTemporaryPagesDirectory() {
        assertTrue(pagesDir.exists(), "Temp pages directory should exist")
    }

    @Given("the pages directory contains:")
    fun thePagesDirectoryContains(table: DataTable) {
        val rows = table.asLists()
        val headers = rows[0]
        val filenameIdx = headers.indexOf("filename")
        val contentIdx = headers.indexOf("content")
        for (i in 1 until rows.size) {
            val row = rows[i]
            val filename = row[filenameIdx]
            val content = row[contentIdx] ?: ""
            File(pagesDir, filename).writeText(content)
        }
    }

    @Given("a codex TOC file:")
    fun aCodexTocFile(tocContent: String) {
        val file = File(tocDir, "toc.adoc")
        file.writeText(tocContent)
        tocFile = file
    }

    @When("I assemble the flat book with title {string} and author {string}")
    fun iAssembleTheFlatBook(title: String, author: String) {
        result = BookAssembler.assemble(pagesDir, title, author, null)
    }

    @When("I assemble the book from the TOC with title {string} and author {string}")
    fun iAssembleTheBookFromTheToc(title: String, author: String) {
        val toc = tocFile ?: fail("A codex TOC file must be written before assembling")
        val sections = BookTocParser.parse(toc)
        assertTrue(sections.isNotEmpty(), "The TOC must yield at least one section")
        val tree = BookTreeBuilder.fromSections(sections)
        result = BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(),
            title = title,
            author = author,
            resolveContent = BookAssembler.contentAwareResolver(pagesDir, null),
        )
    }

    @Then("the assembled book should contain {int} section headings")
    fun theAssembledBookShouldContainSectionHeadings(count: Int) {
        val r = result ?: fail("Book was not assembled")
        val headings = sectionHeadings(r.content)
        assertEquals(
            count,
            headings.size,
            "Expected $count section heading(s) but found ${headings.size}: $headings",
        )
    }

    @Then("the assembled book should contain the heading {string}")
    fun theAssembledBookShouldContainTheHeading(heading: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.lines().any { it.trim() == heading },
            "Expected heading '$heading'. Actual:\n${r.content.take(800)}",
        )
    }

    @Then("the assembled book should carry the anchor {string}")
    fun theAssembledBookShouldCarryTheAnchor(anchor: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.contains(anchor),
            "Expected anchor '$anchor'. Actual:\n${r.content.take(800)}",
        )
    }

    @Then("the assembled book should contain {string}")
    fun theAssembledBookShouldContain(text: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.contains(text),
            "Assembled book should contain '$text'. Actual:\n${r.content.take(800)}",
        )
    }

    @Then("the assembled book should not contain {string}")
    fun theAssembledBookShouldNotContain(text: String) {
        val r = result ?: fail("Book was not assembled")
        assertFalse(
            r.content.contains(text),
            "Assembled book should not contain '$text'",
        )
    }

    @Then("the assembled book should contain {string} before {string}")
    fun theAssembledBookShouldContainBefore(first: String, second: String) {
        val r = result ?: fail("Book was not assembled")
        val firstIdx = r.content.indexOf(first)
        val secondIdx = r.content.indexOf(second)
        assertTrue(firstIdx >= 0, "Assembled book should contain '$first'")
        assertTrue(secondIdx >= 0, "Assembled book should contain '$second'")
        assertTrue(
            firstIdx < secondIdx,
            "'$first' should appear before '$second' (indices $firstIdx / $secondIdx)",
        )
    }

    @Then("the assembled book title should be {string}")
    fun theAssembledBookTitleShouldBe(title: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.startsWith("= $title\n") || r.content.contains("\n= $title\n"),
            "Assembled book should have title '= $title'. Actual:\n${r.content.take(200)}",
        )
    }

    @Then("the assembled book author should be {string}")
    fun theAssembledBookAuthorShouldBe(author: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.contains(":author: $author\n"),
            "Assembled book should have ':author: $author'. Actual:\n${r.content.take(200)}",
        )
    }

    /**
     * Section headings are AsciiDoc level-1 or deeper headings (`==`, `===`, …).
     * The single-`=` title page is deliberately excluded: the point of the
     * fidelity scenarios is that the assembler never fabricates a *section*
     * heading from raw OCR text.
     */
    private fun sectionHeadings(content: String): List<String> =
        content.lines().filter { Regex("^={2,} ").containsMatchIn(it) }
}
