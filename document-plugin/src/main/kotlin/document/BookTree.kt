package document

/**
 * The aggregated structured tree of a book.
 *
 * [BookTree] is the root aggregate of the "Book" domain: it reorganizes the
 * flat list of [BookSection]s (the table of contents produced by
 * [BookTocParser]) into a proper hierarchy of [BookNode]s derived from the
 * `ref` of each section. It is the structural source of truth of the book,
 * fully decoupled from the assembly (which [BookAssembler] performs later).
 *
 * Ink Economy Law: the tree is derived from refs already computed during TOC
 * parsing — no re-OCR, no new I/O.
 */
data class BookTree(
    val root: BookNode,
    val nodesByRef: Map<String, BookNode>,
    val leaves: List<BookSection>,
) {

    /**
     * Returns the node with the given [ref], or `null` if no such node exists
     * (neither real nor synthetic) in the tree.
     */
    fun node(ref: String): BookNode? = nodesByRef[ref]
}
