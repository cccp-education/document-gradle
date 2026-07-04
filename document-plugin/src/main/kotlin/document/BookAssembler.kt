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
}