package document

import document.translation.AsciiDocParser
import document.translation.AsciiDocRenderer
import document.translation.DocumentTranslator
import contracts.i18n.TranslationService

/**
 * Translates a structured [BookTree] into a target language.
 *
 * EPIC DOC-BOOK-TRANSLATE — decisions D1/D2/D3/D5. A book is **not** translated
 * as assembled AsciiDoc: the pivot parser is lossy on book constructions
 * (anchors `[[ref]]`, `:doctype:`, `toc::[]`, navigation), so translating the
 * produced document would destroy the structure. Instead, [BookTranslator]
 * translates the *inputs* of the assembly:
 *
 * - the **titles** of every node and leaf, so [BookAssembler] regenerates the
 *   headings, the table of contents, the `<<ref,title>>` navigation labels and
 *   the anchor references from translated titles (constat #7) — the `ref`
 *   itself is a structural identifier and is never translated;
 * - the **body** of every page, parsed as a header-less fragment
 *   ([AsciiDocParser.parseBody]) and translated through the existing chain
 *   ([DocumentTranslator]), then rendered back as a fragment.
 *
 * The result is a [TranslatedBook]: the translated tree plus a resolver for the
 * translated page bodies. Re-assembly is the caller's job (it owns the
 * [BookLayout]), and it preserves the structure **by construction** — no
 * book construction is ever parsed.
 *
 * The service is Gradle-free and pure apart from the caller's content resolver;
 * a deterministic [TranslationService] fake is enough to unit-test it.
 *
 * Ink Economy Law: a blank or identical target language is a strict no-op —
 * no LLM call, the source tree and resolver are returned unchanged.
 */
class BookTranslator(
    translationService: TranslationService,
    private val parser: AsciiDocParser = AsciiDocParser(),
    private val documentTranslator: DocumentTranslator = DocumentTranslator(translationService),
    private val renderer: AsciiDocRenderer = AsciiDocRenderer(),
) {

    /**
     * @param tree the source book tree
     * @param resolveContent the content resolver of the source book (maps a
     *   [BookSection] to its OCR-ed AsciiDoc body)
     * @param sourceLanguage the language of the source (e.g. `fr`)
     * @param targetLanguage the language to translate to; blank or identical to
     *   [sourceLanguage] means "no translation" (no-op)
     */
    fun translate(
        tree: BookTree,
        resolveContent: (BookSection) -> String,
        sourceLanguage: String,
        targetLanguage: String,
    ): TranslatedBook {
        if (targetLanguage.isBlank() || targetLanguage == sourceLanguage || tree.leaves.isEmpty()) {
            return TranslatedBook(tree, resolveContent)
        }

        val titleCache = mutableMapOf<String, String>()
        val title: (String) -> String = { text ->
            if (text.isBlank()) text
            else titleCache.getOrPut(text) { translateField(text, sourceLanguage, targetLanguage) }
        }

        val translatedRoot = tree.root.copy(
            children = tree.root.children.map { translateNode(it, title) },
        )
        val translatedLeaves = tree.leaves.map { section ->
            section.copy(title = title(section.title))
        }
        val translatedTree = BookTree(
            root = translatedRoot,
            nodesByRef = index(translatedRoot),
            leaves = translatedLeaves,
        )

        val bodyResolver: (BookSection) -> String = { section ->
            resolveContent(section).let { raw ->
                if (raw.isBlank()) raw else translateBody(raw, section.title, sourceLanguage, targetLanguage)
            }
        }

        return TranslatedBook(translatedTree, bodyResolver)
    }

    private fun translateNode(node: BookNode, translateTitle: (String) -> String): BookNode {
        return node.copy(
            title = translateTitle(node.title),
            source = node.source?.copy(title = translateTitle(node.source.title)),
            children = node.children.map { translateNode(it, translateTitle) },
        )
    }

    private fun translateBody(raw: String, title: String, sourceLanguage: String, targetLanguage: String): String {
        val blocks = parser.parseBody(raw)
        if (blocks.isEmpty()) return raw
        val translated = documentTranslator.translateBlocks(blocks, sourceLanguage, targetLanguage, title)
        return renderer.renderBlocks(translated)
    }

    private fun translateField(text: String, sourceLanguage: String, targetLanguage: String): String =
        documentTranslator.translateField(text, sourceLanguage, targetLanguage)

    private fun index(root: BookNode): Map<String, BookNode> {
        val map = LinkedHashMap<String, BookNode>()
        fun walk(node: BookNode) {
            map[node.ref] = node
            node.children.forEach { walk(it) }
        }
        walk(root)
        return map
    }
}
