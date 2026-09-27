package document

import document.translation.DocumentTranslator
import document.translation.FakeTranslationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Dogfooding gate (EPIC DOC-BOOK-TRANSLATE, US-5) — the translated *structure*
 * of the real private content book is proven at scale, **without** a metered
 * LLM call (Règle 2: no publish, no costly run). A deterministic
 * [FakeTranslationService] is enough to prove the core D1 invariant on the real
 * corpus: translating the Book domain and regenerating the structure preserves
 * every `[[ref]]` anchor, the `:doctype: book` and the `toc::[]`, with **0
 * dangling anchor**.
 *
 * The private content never enters this repository: the TOC and scans are read
 * in place (read-only, Règle 7) and the test self-skips (`assumeTrue`) when the
 * corpus is absent.
 */
class BookTranslateContentTest {

    companion object {
        private val CONTENT_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val CONTENT_RICH_TOC = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/Devenir_Formateur_Professionnel_d_Adultes_FPA_II.adoc",
        )
        private val CONTENT_SCANS = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
    }

    @Test
    fun `translating the real content book preserves every anchor with zero dangling reference`() {
        assumeTrue(CONTENT_RICH_TOC.isFile) { "content rich TOC not found at ${CONTENT_RICH_TOC.absolutePath}" }
        assumeTrue(CONTENT_SCANS.isDirectory) { "content scans not found at ${CONTENT_SCANS.absolutePath}" }

        val sections = BookTocParser.parse(CONTENT_RICH_TOC)
        assumeTrue(sections.isNotEmpty()) { "the rich TOC carries no section" }

        val tree = BookTreeBuilder.fromSections(sections)
        val resolveContent = BookAssembler.contentAwareResolver(CONTENT_SCANS)
        val fakeService = FakeTranslationService(" [EN]")
        val translator = BookTranslator(fakeService, documentTranslator = DocumentTranslator(fakeService))

        val sourceBook = BookAssembler.assemble(tree, BookLayout(), "Content Book", "Author", resolveContent).content
        val translated = translator.translate(tree, resolveContent, "fr", "en")
        val translatedBook = BookAssembler.assemble(
            translated.tree,
            BookLayout(),
            "Content Book",
            "Author",
            translated.bodyResolver,
        ).content

        val sourceAnchors = anchors(sourceBook)
        val translatedAnchors = anchorRefs(translatedBook)
        assertTrue(sourceAnchors > 100, "the real book must carry a rich anchor set, got: $sourceAnchors")
        assertEquals(
            sourceAnchors,
            translatedAnchors.size,
            "the translated book must keep every anchor (D1)",
        )
        assertTrue(translatedBook.contains(":doctype: book"), "the doctype must survive the translation")
        assertTrue(translatedBook.contains("toc::[]"), "the toc block must survive the translation")

        // Core structural gate (cadrage D7) — every navigation target the
        // assembled book would emit (`<<ref,title>>`, from BookNumbering over
        // the translated tree) must resolve to an emitted `[[ref]]` anchor:
        // 0 dangling reference. The check is *structural* (domain objects), not
        // a regex over the content — OCR prose legitimately contains `<<`
        // artefacts of the guillemets («).
        val emittedRefs = BookNumbering.emittedNodes(translated.tree).map { it.ref }
        val dangling = emittedRefs.flatMap { ref ->
            val navigation = BookNumbering.navigation(translated.tree, ref)
            listOfNotNull(navigation.previous?.ref, navigation.next?.ref)
        }.filterNot { it in translatedAnchors }
        assertTrue(
            dangling.isEmpty(),
            "the translated book must carry 0 dangling navigation target, found: $dangling",
        )
    }

    private fun anchors(content: String): Int = Regex("""\[\[[^\]]+\]\]""").findAll(content).count()

    private fun anchorRefs(content: String): Set<String> =
        Regex("""\[\[([^\]]+)\]\]""").findAll(content).map { it.groupValues[1] }.toSet()
}
