package document

import document.translation.FakeTranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * EPIC DOC-BOOK-TRANSLATE — US-1: the pure `BookTranslator` domain service.
 *
 * Translating the Book domain means translating its *inputs* (node titles and
 * page bodies) while leaving every structural element (refs, page metadata,
 * anchors) untouched — `BookAssembler` then regenerates anchors, `:doctype:`,
 * `toc::[]` and navigation from the translated tree. These unit tests pin that
 * contract with a deterministic `TranslationService` fake (no TestKit, no LLM).
 */
class BookTranslatorTest {

    private val translator = BookTranslator(FakeTranslationService(" [EN]"))

    private fun tree(): BookTree {
        val sections = listOf(
            BookSection(ref = "0.1", title = "Preface", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "1.1", title = "Chapter One", page = 3, pdfFile = "003.adoc"),
            BookSection(ref = "1.2", title = "Chapter Two", page = 4, pdfFile = "004.adoc"),
        )
        return BookTreeBuilder.fromSections(sections)
    }

    private val bodies = mapOf(
        1 to "Front matter sentence.",
        2 to "Part body sentence.",
        3 to "Chapter body sentence.",
        4 to "Second chapter body sentence.",
    )

    private fun resolver(): (BookSection) -> String = { section -> bodies[section.page].orEmpty() }

    @Test
    fun `node titles are translated while the ref is preserved`() {
        val translated = translator.translate(tree(), resolver(), "fr", "en")

        val node = translated.tree.node("1.1")
        assertEquals("Chapter One [EN]", node?.title)
        assertEquals("1.1", node?.ref)
        assertEquals(3, node?.source?.page)
        assertEquals("003.adoc", node?.source?.pdfFile)
    }

    @Test
    fun `page bodies are translated through the existing pivot chain`() {
        val translated = translator.translate(tree(), resolver(), "fr", "en")

        val leaf = translated.tree.leaves.first { it.ref == "1.1" }
        val body = translated.bodyResolver(leaf)
        assertTrue(body.contains("Chapter body sentence. [EN]"), "body must be translated, got: $body")
    }

    @Test
    fun `the leading paragraph of a page fragment is never lost`() {
        val section = BookSection(ref = "1.1", title = "Chapter One", page = 3, pdfFile = "003.adoc")
        val single = BookTreeBuilder.fromSections(listOf(section))
        val fragment = "Lead paragraph that starts the page.\n\nA second paragraph."

        val translated = translator.translate(single, { fragment }, "fr", "en")
        val body = translated.bodyResolver(translated.tree.leaves.first())

        assertTrue(
            body.contains("Lead paragraph that starts the page. [EN]"),
            "the leading paragraph must survive the header round-trip, got: $body",
        )
    }

    @Test
    fun `a page body made of a table is translated without losing its markup`() {
        val section = BookSection(ref = "1.1", title = "Chapter One", page = 3, pdfFile = "003.adoc")
        val single = BookTreeBuilder.fromSections(listOf(section))
        val fragment = "|===\n| Fixed mindset | Growth mindset\n\n| Failure is a disaster | Failure is a lesson\n|==="

        val translated = translator.translate(single, { fragment }, "fr", "en")
        val body = translated.bodyResolver(translated.tree.leaves.first())

        assertTrue(body.contains("|==="), "the table delimiters must be preserved, got: $body")
        assertTrue(body.contains("Fixed mindset [EN]"), "table cells must be translated, got: $body")
    }

    @Test
    fun `section refs pages and files are preserved on every leaf`() {
        val translated = translator.translate(tree(), resolver(), "fr", "en")

        val leaf = translated.tree.leaves.first { it.ref == "1.2" }
        assertEquals("1.2", leaf.ref)
        assertEquals(4, leaf.page)
        assertEquals("004.adoc", leaf.pdfFile)
        assertEquals("Chapter Two [EN]", leaf.title)
    }

    @Test
    fun `synthetic node titles stay blank and are not translated`() {
        val sections = listOf(BookSection(ref = "1.1", title = "Chapter One", page = 1, pdfFile = "001.adoc"))

        val translated = translator.translate(BookTreeBuilder.fromSections(sections), { "Body." }, "fr", "en")

        val synthetic = translated.tree.node("1")
        assertEquals("", synthetic?.title, "a synthetic ancestor must keep its blank title")
        assertEquals(null, synthetic?.source)
    }

    @Test
    fun `a blank target language is a strict no-op`() {
        val source = tree()
        val translated = translator.translate(source, resolver(), "fr", "")

        assertEquals("Chapter One", translated.tree.node("1.1")?.title)
        val leaf = translated.tree.leaves.first { it.ref == "1.1" }
        assertEquals("Chapter body sentence.", translated.bodyResolver(leaf))
    }

    @Test
    fun `an identical source and target language is a no-op`() {
        val source = tree()
        val translated = translator.translate(source, resolver(), "fr", "fr")

        assertEquals("Chapter One", translated.tree.node("1.1")?.title)
        val leaf = translated.tree.leaves.first { it.ref == "1.1" }
        assertEquals("Chapter body sentence.", translated.bodyResolver(leaf))
    }

    @Test
    fun `anchors doctype and table of contents survive the regeneration`() {
        val source = tree()
        val original = BookAssembler.assemble(source, BookLayout(), "My Book", "Author", resolver()).content
        val translated = translator.translate(source, resolver(), "fr", "en")
        val regenerated = BookAssembler.assemble(
            translated.tree,
            BookLayout(),
            "My Book",
            "Author",
            translated.bodyResolver,
        ).content

        assertEquals(anchors(original), anchors(regenerated), "the anchor count must be preserved")
        assertTrue(regenerated.contains(":doctype: book"), "doctype must survive")
        assertTrue(regenerated.contains("toc::[]"), "the toc block must survive")
        assertFalse(regenerated.contains("[[1.1]] [EN]"), "a ref anchor must never be translated")
        assertTrue(regenerated.contains("[[1.1]]"), "the ref anchor must be intact")
    }

    @Test
    fun `navigation labels follow the translated titles`() {
        val source = tree()
        val translated = translator.translate(source, resolver(), "fr", "en")
        val regenerated = BookAssembler.assemble(
            translated.tree,
            BookLayout(emitNavigation = true, pageBreakBetweenNodes = false),
            "My Book",
            "Author",
            translated.bodyResolver,
        ).content

        assertTrue(
            regenerated.contains("<<1.1,Chapter One [EN]>>"),
            "navigation must use the translated titles, got: $regenerated",
        )
    }

    private fun anchors(content: String): Int = Regex("""\[\[[^\]]+\]\]""").findAll(content).count()
}
