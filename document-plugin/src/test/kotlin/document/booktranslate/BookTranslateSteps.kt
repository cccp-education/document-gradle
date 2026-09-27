package document.booktranslate

import document.BookAssembler
import document.BookLayout
import document.BookSection
import document.BookTranslator
import document.BookTree
import document.BookTreeBuilder
import document.TranslatedBook
import document.translation.FakeTranslationService
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Step definitions for the `@book-translate` scenarios (EPIC
 * DOC-BOOK-TRANSLATE, US-4).
 *
 * Pure BDD: the steps exercise the translation domain directly
 * ([BookTranslator], [BookAssembler]) with a deterministic fake translation
 * service — no Gradle task, no external I/O. All step texts carry the
 * `book-translate ` prefix (anti-glue-collision pattern). The scenarios pin
 * the core D1 proof: translating the domain and regenerating the structure
 * preserves anchors, doctype and toc (the *source* book is never parsed).
 */
class BookTranslateSteps {

    private var sections: List<BookSection> = emptyList()
    private var tree: BookTree? = null
    private var translated: TranslatedBook? = null
    private var sourceAssembled: String = ""
    private var translatedAssembled: String = ""
    private var emitNavigation: Boolean = false

    private fun resolver(): (BookSection) -> String = { section -> "Body of ${section.ref}" }

    @Given("^book-translate a TOC with refs (.+)$")
    fun `book-translate a TOC with refs`(refsText: String) {
        val refs = Regex("\"([^\"]*)\"").findAll(refsText).map { it.groupValues[1] }.toList()
        require(refs.isNotEmpty()) { "at least one ref must be given, got: '$refsText'" }
        sections = refs.mapIndexed { index, ref ->
            BookSection(ref = ref, title = "Chapter $ref", page = index + 1, pdfFile = "%03d.adoc".format(index + 1))
        }
        tree = BookTreeBuilder.fromSections(sections)
    }

    @Given("book-translate OCR pages for every referenced section")
    fun `book-translate OCR pages for every referenced section`() {
        require(sections.isNotEmpty()) { "a TOC must be given before the OCR pages" }
    }

    @When("^book-translate the book is translated from \"([^\"]*)\" to \"([^\"]*)\"$")
    fun `book-translate the book is translated`(source: String, target: String) {
        translate(source, target, navigation = false)
    }

    @When("^book-translate the book is translated and assembled with navigation$")
    fun `book-translate the book is translated and assembled with navigation`() {
        translate("fr", "en", navigation = true)
    }

    private fun translate(source: String, target: String, navigation: Boolean) {
        val sourceTree = requireNotNull(tree) { "a TOC must be given first" }
        val translator = BookTranslator(FakeTranslationService(" [EN]"))
        val translatedBook = translator.translate(sourceTree, resolver(), source, target)
        translated = translatedBook
        emitNavigation = navigation
        sourceAssembled = assemble(sourceTree, resolver(), navigation)
        translatedAssembled = assemble(translatedBook.tree, translatedBook.bodyResolver, navigation)
    }

    private fun assemble(tree: BookTree, resolve: (BookSection) -> String, navigation: Boolean): String =
        BookAssembler.assemble(
            tree = tree,
            layout = BookLayout(emitNavigation = navigation, pageBreakBetweenNodes = false),
            title = "Translated Book",
            author = "Cheroliv",
            resolveContent = resolve,
        ).content

    @Then("^book-translate node \"([^\"]+)\" is titled \"([^\"]+)\"$")
    fun `book-translate node is titled`(ref: String, expected: String) {
        val node = requireNotNull(translated).tree.node(ref)
        assertEquals(expected, node?.title, "node '$ref' title mismatch")
    }

    @Then("^book-translate the ref \"([^\"]+)\" is preserved$")
    fun `book-translate the ref is preserved`(ref: String) {
        assertTrue(
            requireNotNull(translated).tree.node(ref) != null,
            "the ref '$ref' must survive the translation",
        )
    }

    @Then("^book-translate section \"([^\"]+)\" carries the translated body$")
    fun `book-translate section carries the translated body`(ref: String) {
        assertTrue(
            translatedAssembled.contains("Body of $ref [EN]"),
            "section '$ref' must carry the translated body, got:\n$translatedAssembled",
        )
    }

    @Then("^book-translate section \"([^\"]+)\" carries the original body$")
    fun `book-translate section carries the original body`(ref: String) {
        assertTrue(
            translatedAssembled.contains("Body of $ref") && !translatedAssembled.contains("Body of $ref [EN]"),
            "section '$ref' must carry the untranslated body, got:\n$translatedAssembled",
        )
    }

    @Then("book-translate the regenerated book carries the same number of anchors as the source")
    fun `book-translate the regenerated book carries the same number of anchors as the source`() {
        assertEquals(
            anchors(sourceAssembled),
            anchors(translatedAssembled),
            "the anchor count must be preserved (D1), source:\n$sourceAssembled\nregenerated:\n$translatedAssembled",
        )
    }

    @Then("book-translate the regenerated book carries the doctype")
    fun `book-translate the regenerated book carries the doctype`() {
        assertTrue(translatedAssembled.contains(":doctype: book"), "the doctype must survive:\n$translatedAssembled")
    }

    @Then("book-translate the regenerated book carries the toc block")
    fun `book-translate the regenerated book carries the toc block`() {
        assertTrue(translatedAssembled.contains("toc::[]"), "the toc block must survive:\n$translatedAssembled")
    }

    @Then("^book-translate section \"([^\"]+)\" links forward to \"([^\"]+)\" using the translated titles$")
    fun `book-translate section links forward using the translated titles`(ref: String, next: String) {
        assertTrue(emitNavigation, "the book must have been assembled with navigation")
        assertTrue(
            translatedAssembled.contains("<<$next,Chapter $next [EN]>>"),
            "section '$ref' must link forward to '$next' with the translated title, got:\n$translatedAssembled",
        )
        assertFalse(
            translatedAssembled.contains("<<$next,Chapter $next>>"),
            "the navigation must not use the untranslated title",
        )
    }

    private fun anchors(content: String): Int = Regex("""\[\[[^\]]+\]\]""").findAll(content).count()
}
