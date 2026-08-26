package document.crossborough

import document.BookAssembler
import io.cucumber.java.After
import io.cucumber.java.Before
import io.cucumber.java.en.Given
import io.cucumber.java.en.When
import io.cucumber.java.en.Then
import io.cucumber.datatable.DataTable
import org.junit.jupiter.api.Assertions.*
import java.io.File
import java.nio.file.Files
import java.util.regex.Pattern

class CrossBoroughSteps {

    private lateinit var pagesDir: File
    private var result: document.BookAssemblyResult? = null

    @Before
    fun setUp() {
        pagesDir = Files.createTempDirectory("cross-borough-book").toFile()
        result = null
    }

    @After
    fun tearDown() {
        pagesDir.deleteRecursively()
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

    @When("I assemble the book with title {string} and author {string}")
    fun iAssembleTheBook(title: String, author: String) {
        result = BookAssembler.assemble(pagesDir, title, author, null)
    }

    @Then("the assembled book should contain {int} page headings")
    fun theAssembledBookShouldContainPageHeadings(count: Int) {
        val r = result ?: fail("Book was not assembled")
        val headingCount = countOccurrences(r.content, "= ")
        // The main title + page headings
        assertEquals(count + 1, headingCount, "Expected ${count + 1} total headings (1 main title + $count page headings) but found $headingCount")
    }

    @Then("the assembled book should contain {string}")
    fun theAssembledBookShouldContain(text: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.contains(text),
            "Assembled book should contain '$text'. Actual:\n${r.content.take(500)}"
        )
    }

    @Then("the assembled book title should be {string}")
    fun theAssembledBookTitleShouldBe(title: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.startsWith("= $title\n") || 
            r.content.contains("\n= $title\n") ||
            r.content.contains("= $title\n:author:"),
            "Assembled book should have title '= $title'. Actual:\n${r.content.take(200)}"
        )
    }

    @Then("the assembled book author should be {string}")
    fun theAssembledBookAuthorShouldBe(author: String) {
        val r = result ?: fail("Book was not assembled")
        assertTrue(
            r.content.contains(":author: $author\n") ||
            r.content.contains(":author: $author\n:doctype:"),
            "Assembled book should have ':author: $author'. Actual:\n${r.content.take(200)}"
        )
    }

    @Then("the first page heading should be {string}")
    fun theFirstPageHeadingShouldBe(heading: String) {
        val r = result ?: fail("Book was not assembled")
        val headings = extractHeadings(r.content)
        // Skip the main title (first heading) to get to page headings
        assertTrue(
            headings.size >= 2 && headings[1] == heading,
            "First page heading should be '$heading'. Actual headings: $headings"
        )
    }

    @Then("the second page heading should be {string}")
    fun theSecondPageHeadingShouldBe(heading: String) {
        val r = result ?: fail("Book was not assembled")
        val headings = extractHeadings(r.content)
        // Skip the main title (first heading) to get to page headings
        assertTrue(
            headings.size >= 3 && headings[2] == heading,
            "Second page heading should be '$heading'. Actual headings: $headings"
        )
    }

    @Then("the third page heading should be {string}")
    fun theThirdPageHeadingShouldBe(heading: String) {
        val r = result ?: fail("Book was not assembled")
        val headings = extractHeadings(r.content)
        // Skip the main title (first heading) to get to page headings
        assertTrue(
            headings.size >= 4 && headings[3] == heading,
            "Third page heading should be '$heading'. Actual headings: $headings"
        )
    }

    private fun countOccurrences(content: String, substring: String): Int {
        if (content.isEmpty() || substring.isEmpty()) return 0
        var count = 0
        var idx = content.indexOf(substring)
        while (idx != -1) {
            count++
            idx = content.indexOf(substring, idx + substring.length)
        }
        return count
    }

    private fun extractHeadings(content: String): List<String> {
        val headings = mutableListOf<String>()
        val pattern = Pattern.compile("= (.+)")
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            headings.add(matcher.group(1).trim())
        }
        return headings
    }
}
