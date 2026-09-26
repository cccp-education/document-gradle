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
 */
class TranslatedBook(
    val tree: BookTree,
    val bodyResolver: (BookSection) -> String,
)
