package document

/**
 * Page-layout strategy for emitting a structured book as AsciiDoc.
 *
 * [BookLayout] is a pure value object describing *how* a [BookTree] is turned
 * into navigable AsciiDoc: which heading level corresponds to a node depth,
 * whether a page break is inserted between top-level nodes, whether a title
 * page and a table of contents are emitted, whether previous / next
 * cross-references are appended. It contains no I/O and no Gradle
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
    val imagesDir: String? = null,
    /**
     * DOC-BOOK-MATTER — emit an AsciiDoc hard page break (`<<<`) at every
     * *matter transition* (FRONT→BODY, BODY→BACK) and close the dedicated title
     * page. Off by default (backward compatible): the assembled book keeps its
     * exact previous layout.
     */
    val emitMatterBreaks: Boolean = false,
    /**
     * DOC-BOOK-MATTER — the policy that classifies a node's `ref` into a
     * [Matter]. [MatterPolicy.DEFAULT] keeps the historical `0` / `9`
     * convention; a real book may inject [MatterPolicy.derive] from its TOC.
     */
    val matterPolicy: MatterPolicy = MatterPolicy.DEFAULT,
    /**
     * DOC-BOOK-CONSISTENCY-B6 — emit a previous / next cross-reference at the
     * foot of every emitted section. Off by default (backward compatible): the
     * assembled book keeps its exact previous layout.
     */
    val emitNavigation: Boolean = false,
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
     * DOC-BOOK-MATTER — emits the hard page break that separates two matters.
     * Same AsciiDoc primitive as [pageBreak]; named for the domain intent so
     * call sites read as "a matter boundary", not "an arbitrary break".
     */
    fun matterBreak(): String = "<<<"

    /**
     * DOC-BOOK-CONSISTENCY-B6 — emits the AsciiDoc previous / next navigation
     * line for [navigation] (`<<ref,title>>` cross-references). Either side may
     * be absent at the boundaries of the book; a standalone section with no
     * neighbour yields an empty string (nothing to emit).
     */
    fun navigationLinks(navigation: BookNavigation): String {
        val links = buildList {
            navigation.previous?.let { add("<<${it.ref},${it.title}>>") }
            navigation.next?.let { add("<<${it.ref},${it.title}>>") }
        }
        return links.joinToString(" | ")
    }

    /**
     * Emits the AsciiDoc table-of-contents attribute as a *macro* (`:toc:
     * macro`), which — combined with [tableOfContentsBlock] (`toc::[]`) —
     * makes Asciidoctor actually render a navigable table of contents in the
     * HTML/PDF/EPUB outputs (a bare `:toc:` alone is not always rendered).
     */
    fun tableOfContents(): String = ":toc: macro"

    /**
     * Emits the `toc::[]` block macro that materialises the table of contents
     * at its location in the document body. Must follow [tableOfContents].
     */
    fun tableOfContentsBlock(): String = "toc::[]"

    /**
     * Emits the book title page header (`= Title` + `:author:` + `:doctype: book`).
     *
     * When [imagesDir] is set, an `:imagesdir:` attribute is emitted too: it
     * tells Asciidoctor where to resolve the relative `image::` targets emitted
     * by the assembler, so the HTML/PDF/EPUB outputs actually *embed* the page
     * scans instead of referencing missing resources (RSC-007 otherwise).
     */
    fun titlePage(title: String, author: String): String {
        val sb = StringBuilder()
        sb.append("= ").append(title).append("\n")
        sb.append(":author: ").append(author).append("\n")
        sb.append(":doctype: book")
        imagesDir?.let { sb.append("\n").append(":imagesdir: ").append(it) }
        return sb.toString()
    }
}
