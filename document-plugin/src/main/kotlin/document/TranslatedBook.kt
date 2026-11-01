package document

/**
 * The result of translating a [BookTree] into a target language.
 *
 * EPIC DOC-BOOK-TRANSLATE — D5. A [TranslatedBook] carries the *inputs* of the
 * assembly: an updated [tree] whose node titles (and leaf titles) are
 * translated, and a [bodyResolver] that yields the translated AsciiDoc of a
 * physical page. The structural elements of the book — `ref`, page number,
 * PDF file, anchors, `:doctype:`, `toc::[]`, navigation — are **not** part of
 * this object: [BookAssembler] regenerates them from [tree], so they are
 * preserved by construction (decision D1/D4).
 *
 * The resolver is the only element allowed to perform I/O (it is the caller's
 * content resolver, wrapped with translation); the tree itself is a pure value.
 *
 * XF-FP3-2 — the resolver is **memoised by section ref**: assembling an
 * illustrated book calls it twice (once to discover the referenced scans, once
 * with the relative `:imagesdir:`), and re-translating would double the LLM
 * cost for every page. Memoisation keeps the "one translation per page"
 * property (Ink Economy Law) and makes the diagnostics collected through
 * [BookTranslator] exact (one trace per page, never duplicated).
 */
class TranslatedBook(
    val tree: BookTree,
    resolveBody: (BookSection) -> String,
) {
    private val cache = HashMap<String, String>()

    val bodyResolver: (BookSection) -> String = { section ->
        cache.getOrPut(section.ref) { resolveBody(section) }
    }
}
