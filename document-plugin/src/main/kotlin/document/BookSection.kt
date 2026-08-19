package document

/**
 * A single section entry from the book's table of contents.
 *
 * A [BookSection] captures the mapping between a TOC reference (e.g. `1.0.1`),
 * its title, the physical page number in the book, and the PDF file that
 * contains the scanned content for that section.
 *
 * The [BookValidator] consumes a list of [BookSection]s to verify that every
 * section has a corresponding non-empty AsciiDoc page, and that every
 * referenced PDF exists.
 */
data class BookSection(
    val ref: String,
    val title: String,
    val page: Int,
    val pdfFile: String,
) {

    init {
        require(ref.isNotBlank()) { "BookSection ref must not be blank" }
        require(title.isNotBlank()) { "BookSection title must not be blank" }
        require(page >= 0) { "BookSection page must be non-negative, got: $page" }
        require(pdfFile.isNotBlank()) { "BookSection pdfFile must not be blank" }
    }
}