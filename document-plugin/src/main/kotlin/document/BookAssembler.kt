package document

import java.io.File

/**
 * Assembles a set of OCR-ed AsciiDoc pages (produced by codex-gradle)
 * into a single book document.
 *
 * A [BookAssembler] reads all [BookPage]s from a pages directory, orders
 * them by [PageOrder], and merges their content under a single AsciiDoc
 * document header (`= Title` + `:author:`).
 *
 * DOC-11 — Book pipeline Codex -> Document:
 * - Codex (Brooklyn) produces one `.adoc` file per page via `collectOcr`
 * - [BookAssembler] merges them into one book, preserving page order
 * - Optional photos are referenced as `image::` directives
 * - The source page files are never mutated (Rule 7)
 *
 * Ink Economy Law: the assembly is deterministic — the same set of pages
 * in the same order always produces the same AsciiDoc book.
 */
object BookAssembler {

    private const val DOUBLE_NEWLINE = "\n\n"
    private const val PAGE_SEPARATOR = "\n\n"

    /**
     * Assembles all `.adoc` pages from [pagesDir] into a single book.
     *
     * @param pagesDir the directory containing the OCR-ed AsciiDoc pages
     * @param title the book title (AsciiDoc level-0 header)
     * @param author the book author (`:author:` attribute)
     * @param photosDir optional directory of original page photos to
     *   reference as illustrations (image:: directives)
     * @return the [BookAssemblyResult] containing the merged AsciiDoc content
     */
    fun assemble(
        pagesDir: File,
        title: String,
        author: String,
        photosDir: File? = null,
    ): BookAssemblyResult {
        val pages = loadPages(pagesDir, photosDir)
        val header = buildHeader(title, author)
        val body = buildBody(pages)
        val content = header + DOUBLE_NEWLINE + body
        val photoCount = pages.count { it.photo != null }
        return BookAssemblyResult(content = content, pages = pages, photoCount = photoCount)
    }

    private fun loadPages(pagesDir: File, photosDir: File?): List<BookPage> {
        if (!pagesDir.exists() || !pagesDir.isDirectory) return emptyList()
        return pagesDir.listFiles { f -> f.isFile && f.extension.equals("adoc", ignoreCase = true) }
            ?.sortedBy { it.name }
            ?.map { BookPage.fromFile(it, photosDir) }
            ?.sortedWith(compareBy({ it.order }, { it.name }))
            ?: emptyList()
    }

    private fun buildHeader(title: String, author: String): String {
        val sb = StringBuilder()
        sb.append("= ").append(title).append("\n")
        sb.append(":author: ").append(author).append("\n")
        sb.append(":doctype: book")
        return sb.toString()
    }

    private fun buildBody(pages: List<BookPage>): String {
        if (pages.isEmpty()) return """"""
        val sb = StringBuilder()
        pages.forEachIndexed { index, page ->
            if (index > 0) sb.append(PAGE_SEPARATOR)
            val text = page.readText().trim()
            sb.append(text)
            page.photo?.let { photo ->
                sb.append(PAGE_SEPARATOR)
                sb.append("image::").append(photo.name).append("[]")
            }
        }
        return sb.toString().trim()
    }

    /**
     * Assembles a [BookTree] into a single structured, navigable book.
     *
     * This is the structured counterpart of [assemble] (DOC-11 blob-plat):
     * instead of concatenating flat OCR pages, it walks the [BookTree] and
     * emits one AsciiDoc heading per real section at its own [BookNode.level]
     * (so the `doctype: book` hierarchy — parts `==`, chapters `===`,
     * sections `====` — is restored), prefixed with its hierarchical
     * [BookNumbering] and an `[[ref]]` cross-reference anchor, followed by the
     * resolved page content. A page break is inserted between top-level nodes
     * when [BookLayout.pageBreakBetweenNodes] is set.
     *
     * Front / body / back matter are naturally ordered because the tree already
     * carries the `0.x` / `1.x` / `9.x` refs produced by [BookTocParser].
     *
     * @param tree the structured book tree (from [BookTreeBuilder.fromSections])
     * @param layout the page-layout strategy (title page, toc, page breaks)
     * @param title the book title (level-0 header)
     * @param author the book author (`:author:` attribute)
     * @param resolveContent maps a [BookSection] (one physical page) to its
     *   OCR-ed AsciiDoc text; multi-page sections are concatenated in order
     * @return the [BookAssemblyResult] with the structured AsciiDoc content
     */
    fun assemble(
        tree: BookTree,
        layout: BookLayout,
        title: String,
        author: String,
        resolveContent: (BookSection) -> String,
    ): BookAssemblyResult {
        val sb = StringBuilder()
        if (layout.emitTitlePage) {
            sb.append(layout.titlePage(title, author))
            // `:toc: macro` must stay inside the document header (no blank line
            // before it) so Asciidoctor enables the toc; the `toc::[]` block is
            // emitted in the body just below.
            if (layout.emitTableOfContents) sb.append("\n").append(layout.tableOfContents())
        } else if (layout.emitTableOfContents) {
            sb.append(layout.tableOfContents()).append("\n")
        }
        if (layout.emitTableOfContents) {
            sb.append("\n\n").append(layout.tableOfContentsBlock())
        }

        val body = buildStructuredBody(tree, layout, resolveContent)
        if (body.isNotBlank()) {
            if (sb.isNotEmpty()) sb.append("\n\n")
            sb.append(body)
        }

        return BookAssemblyResult(content = sb.toString(), pages = emptyList(), photoCount = 0)
    }

    /**
     * Builds a [File]-backed content resolver for [assemble]: each [BookSection]
     * is matched to the OCR page whose numeric file-name prefix equals its
     * physical [BookSection.page] (the `%03d-*.adoc` convention of codex-gradle).
     */
    fun pageContentResolver(pagesDir: File): (BookSection) -> String {
        val byOrder = loadPages(pagesDir, null).associateBy { it.order.value }
        return { section -> byOrder[section.page]?.readText()?.trim() ?: "" }
    }

    /**
     * Resolves OCR page content for both naming conventions used across the
     * corpus:
     * - the FPA scans (`NNN.adoc` + `NNN_N.adoc` splits) via [FpaPageResolver];
     * - the codex `%03d-*.adoc` convention via [pageContentResolver].
     *
     * The FPA resolver is tried first; only when it yields nothing do we fall
     * back to the legacy codex resolver, so the same [AssembleBookTask] serves
     * every consumer without a convention flag.
     */
    fun fpaAwareResolver(pagesDir: File): (BookSection) -> String {
        val fpa = FpaPageResolver(pagesDir)
        val legacy = pageContentResolver(pagesDir)
        return { section ->
            val fromFpa = fpa.content(section)
            if (fromFpa.isNotEmpty()) fromFpa else legacy(section)
        }
    }

    private fun buildStructuredBody(
        tree: BookTree,
        layout: BookLayout,
        resolve: (BookSection) -> String,
    ): String {
        val numbers = BookNumbering.numbers(tree)
        val blocks = mutableListOf<Pair<Int, String>>()

        fun emit(node: BookNode) {
            if (node.source != null) {
                val title = node.title.ifBlank { node.ref }
                val num = numbers[node.ref]
                val headingTitle = if (num != null) "$num. $title" else title
                val heading = layout.heading(node.level + 1, headingTitle)
                val anchor = BookNumbering.anchor(node.ref)
                val sections = tree.leaves.filter { it.ref == node.ref }
                val content = sections.joinToString("\n\n") { resolve(it).trim() }.trim()
                val block = buildString {
                    append(anchor)
                    append("\n")
                    append(heading)
                    if (content.isNotEmpty()) {
                        append("\n\n")
                        append(content)
                    }
                }
                blocks.add(node.level to block)
            }
            node.children.forEach { emit(it) }
        }
        emit(tree.root)

        if (blocks.isEmpty()) return ""
        val out = StringBuilder()
        blocks.forEachIndexed { i, (level, text) ->
            if (i > 0 && blocks[i - 1].first == 0 && layout.pageBreakBetweenNodes) {
                out.append(layout.pageBreak()).append("\n\n")
            }
            out.append(text)
            if (i < blocks.lastIndex) out.append("\n\n")
        }
        return out.toString()
    }
}