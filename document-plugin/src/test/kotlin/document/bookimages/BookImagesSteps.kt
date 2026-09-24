package document.bookimages

import document.BookAssembler
import document.BookImageResolver
import document.BookImageRewriter
import document.BookLayout
import document.BookSection
import document.BookTreeBuilder
import document.ContentPageResolver
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-images` scenarios (EPIC DOC-BOOK-IMAGES,
 * code-review S-258 B4/B5).
 *
 * Pure BDD: the steps exercise the image domain objects directly
 * ([BookImageResolver], [BookImageRewriter], [BookPageRenderer],
 * [ContentPageResolver], [BookAssembler]) — no Gradle task, no real corpus.
 * All step texts carry the `book-images ` prefix (anti-glue-collision pattern).
 */
class BookImagesSteps {

    private var scansDir: File = Files.createTempDirectory("book-images").toFile().apply { deleteOnExit() }
    private var sections: List<BookSection> = emptyList()
    private var content: String = ""
    private var assembled: String = ""
    private var resolver: ContentPageResolver? = null

    @Given("^book-images a scans directory containing (.+)$")
    fun `book-images a scans directory containing`(namesText: String) {
        scansDir.mkdirs()
        Regex("\"([^\"]+)\"").findAll(namesText).map { it.groupValues[1] }.forEach { name ->
            File(scansDir, name).writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        }
    }

    @Then("book-images the scan of page {int} is {string}")
    fun `book-images the scan of page is`(page: Int, expected: String) {
        val scan = BookImageResolver.scanFor(page, scansDir)
        assertEquals(expected, scan?.name, "scan of page $page mismatch")
    }

    @Then("book-images the scan of page {int} is absent")
    fun `book-images the scan of page is absent`(page: Int) {
        assertNull(BookImageResolver.scanFor(page, scansDir), "no scan must exist for page $page")
    }

    @Given("^book-images a page \"([^\"]+)\" containing$")
    fun `book-images a page containing`(fileName: String, body: String) {
        scansDir.mkdirs()
        File(scansDir, fileName).writeText(body.trimIndent())
    }

    @Given("book-images the page has a scan {string}")
    fun `book-images the page has a scan`(scanName: String) {
        File(scansDir, scanName).writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    }

    @Given("book-images the page has no scan")
    fun `book-images the page has no scan`() {
        // Nothing to create — the absence is the fixture.
    }

    @Given("book-images the scans directory also contains {string}")
    fun `book-images the scans directory also contains`(name: String) {
        File(scansDir, name).writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
    }

    @When("book-images the page content is resolved with illustration")
    fun `book-images the page content is resolved with illustration`() {
        resolver = ContentPageResolver(scansDir, photosDir = scansDir)
    }

    @Then("book-images the content references the scan {string}")
    fun `book-images the content references the scan`(scanName: String) {
        val page = currentPage()
        val resolved = resolver!!.content(sectionFor(page))
        assertTrue(
            "image::$scanName[" in resolved || "image:$scanName[" in resolved,
            "the resolved content must reference '$scanName', got:\n$resolved",
        )
    }

    @Then("book-images the content no longer references {string}")
    fun `book-images the content no longer references`(ghost: String) {
        val page = currentPage()
        val resolved = resolver!!.content(sectionFor(page))
        assertFalse(ghost in resolved, "the ghost '$ghost' must disappear, got:\n$resolved")
    }

    @Then("book-images the content references no image")
    fun `book-images the content references no image`() {
        val page = currentPage()
        val resolved = resolver!!.content(sectionFor(page))
        assertFalse("image::" in resolved || "image:" in resolved, "no image must be referenced, got:\n$resolved")
    }

    @Then("book-images the content still contains {string}")
    fun `book-images the content still contains`(text: String) {
        val page = currentPage()
        val resolved = resolver!!.content(sectionFor(page))
        assertTrue(text in resolved, "the surrounding text must survive, got:\n$resolved")
    }

    @Then("book-images the content references {string}")
    fun `book-images the content references`(target: String) {
        val page = currentPage()
        val resolved = resolver!!.content(sectionFor(page))
        assertTrue(target in resolved, "the content must reference '$target', got:\n$resolved")
    }

    // --- structured book scenarios ---

    @Given("^book-images a TOC with refs (.+)$")
    fun `book-images a TOC with refs`(refsText: String) {
        val refs = Regex("\"([^\"]+)\"").findAll(refsText).map { it.groupValues[1] }.toList()
        sections = refs.mapIndexed { index, ref ->
            BookSection(ref = ref, title = "Section $ref", page = index + 1, pdfFile = "%03d.adoc".format(index + 1))
        }
    }

    @Given("book-images OCR pages for every referenced section")
    fun `book-images OCR pages for every referenced section`() {
        scansDir.mkdirs()
        sections.forEach { section ->
            File(scansDir, "%03d.adoc".format(section.page)).writeText("== Section ${section.ref}\n\nBody of ${section.ref}.")
        }
    }

    @Given("book-images every referenced page has a scan")
    fun `book-images every referenced page has a scan`() {
        sections.forEach { section ->
            File(scansDir, "%03d.jpg".format(section.page)).writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte()))
        }
    }

    @When("book-images the structured book is assembled with illustration")
    fun `book-images the structured book is assembled with illustration`() {
        assembled = assembleBook(illustrated = true)
    }

    @When("book-images the structured book is assembled without illustration")
    fun `book-images the structured book is assembled without illustration`() {
        assembled = assembleBook(illustrated = false)
    }

    @Then("book-images the book emits an imagesdir attribute")
    fun `book-images the book emits an imagesdir attribute`() {
        assertTrue(":imagesdir:" in assembled, "the imagesdir attribute must be emitted, got:\n${assembled.take(300)}")
    }

    @Then("book-images the book emits no imagesdir attribute")
    fun `book-images the book emits no imagesdir attribute`() {
        assertFalse(":imagesdir:" in assembled, "no imagesdir must be emitted, got:\n${assembled.take(300)}")
    }

    @Then("book-images the book references the scan of every page")
    fun `book-images the book references the scan of every page`() {
        sections.forEach { section ->
            val expected = "image::%03d.jpg[]".format(section.page)
            assertTrue(expected in assembled, "the book must reference '$expected', got:\n$assembled")
        }
    }

    @Then("book-images the book references no image")
    fun `book-images the book references no image`() {
        assertFalse("image::" in assembled, "no image must be referenced, got:\n$assembled")
    }

    private fun assembleBook(illustrated: Boolean): String {
        val tree = BookTreeBuilder.fromSections(sections)
        val photos = if (illustrated) scansDir else null
        val resolver = BookAssembler.contentAwareResolver(scansDir, photos)
        // Mirror the production contract (AssembleBookTask): emit :imagesdir:
        // only when the assembled content actually references a resolvable scan.
        val first = BookAssembler.assemble(tree, BookLayout(), "Image Book", "Cheroliv", resolver).content
        val referenced = BookImageRewriter.targets(first).any { scansDir.resolve(it).isFile }
        val layout = if (referenced) BookLayout(imagesDir = "images") else BookLayout()
        return BookAssembler.assemble(tree, layout, "Image Book", "Cheroliv", resolver).content
    }

    private fun currentPage(): File =
        scansDir.listFiles { f -> f.extension.equals("adoc", ignoreCase = true) }
            ?.single()
            ?: error("exactly one page must be present, got: ${scansDir.list()}")

    private fun sectionFor(page: File): BookSection {
        val number = page.nameWithoutExtension.takeWhile { it.isDigit() }.toIntOrNull() ?: 1
        return BookSection(ref = "1", title = "Section", page = number, pdfFile = page.name)
    }
}
