package document

/**
 * Page-layout strategy for emitting a structured book as AsciiDoc.
 *
 * [BookLayout] is a pure value object describing *how* a [BookTree] is turned
 * into navigable AsciiDoc: which heading level corresponds to a node depth,
 * whether a page break is inserted between top-level nodes, whether a title
 * page and a table of contents are emitted. It contains no I/O and no Gradle
 * dependency — every method returns the AsciiDoc `String` it would emit, so it
 * is fully unit-testable.
 *
 * Heading levels follow AsciiDoc `book` doctype conventions: a node at
 * [BookNode.level] `k` (k >= 1) is emitted as `=` repeated `k + 1` times
 * (`==` for level 1, `===` for level 2, …). The synthetic root (level 0) is
 * never emitted as a heading — it is replaced by the explicit title page.
 *
 * Ink Economy Law: the layout is a deterministic pure function of the tree and
 * its own configuration — the same inputs always yield the same AsciiDoc.
 */
data class BookLayout(
    val emitTitlePage: Boolean = true,
    val emitTableOfContents: Boolean = true,
    val pageBreakBetweenNodes: Boolean = true,
) {

    /**
     * Emits the AsciiDoc heading for a node at [level] with [title].
     *
     * @throws IllegalArgumentException if [level] < 1 (the root is emitted via
     *   [titlePage], never as a heading)
     */
    fun heading(level: Int, title: String): String {
        require(level >= 1) { "heading level must be >= 1, got: $level" }
        return "=".repeat(level + 1) + " " + title
    }

    /**
     * Emits an AsciiDoc hard page break (`<<<` on its own line).
     */
    fun pageBreak(): String = "<<<"

    /**
     * Emits the AsciiDoc table-of-contents attribute (`:toc:`).
     */
    fun tableOfContents(): String = ":toc:"

    /**
     * Emits the book title page header (`= Title` + `:author:` + `:doctype: book`).
     */
    fun titlePage(title: String, author: String): String {
        val sb = StringBuilder()
        sb.append("= ").append(title).append("\n")
        sb.append(":author: ").append(author).append("\n")
        sb.append(":doctype: book")
        return sb.toString()
    }
}
