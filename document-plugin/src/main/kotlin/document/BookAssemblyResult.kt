package document

import java.io.File

/**
 * Result of assembling a set of OCR-ed [BookPage]s into a single book.
 *
 * [BookAssemblyResult] is the value object produced by [BookAssembler].
 * It carries the assembled AsciiDoc content, the ordered list of pages
 * that were merged, and the count of photos that were referenced as
 * illustrations.
 *
 * Ink Economy Law: the assembled content is deterministic — given the same
 * set of pages in the same order, the assembler always produces the same
 * AsciiDoc output.
 */
data class BookAssemblyResult(
    val content: String,
    val pages: List<BookPage>,
    val photoCount: Int,
) {

    val pageCount: Int get() = pages.size

    /**
     * Writes the assembled AsciiDoc content to [target] without mutating
     * any of the source page files (Rule 7 — DOCUMENT CONSOMME ASCIIDOC).
     */
    fun writeTo(target: File) {
        target.parentFile.mkdirs()
        target.writeText(content)
    }
}