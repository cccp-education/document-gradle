package document

/**
 * Output format toggles for the document pipeline (DOC-12).
 *
 * Ubiquitous language: a [DocumentOutputs] groups the five boolean flags that
 * select which AsciidoctorJ backends are invoked. It is the value object
 * behind the nested `outputs { }` block of the unified `document { }` DSL.
 *
 * Default mirrors the historical convention: HTML only.
 */
data class DocumentOutputs(
    val html: Boolean = true,
    val pdf: Boolean = false,
    val epub: Boolean = false,
    val docbook: Boolean = false,
    val manpage: Boolean = false,
) {

    /**
     * Returns the list of [DocumentFormat]s enabled by the toggles,
     * in the canonical enum order (HTML, PDF, EPUB, DOCBOOK, MANPAGE).
     */
    fun enabledFormats(): List<DocumentFormat> = buildList {
        if (html) add(DocumentFormat.HTML)
        if (pdf) add(DocumentFormat.PDF)
        if (epub) add(DocumentFormat.EPUB)
        if (docbook) add(DocumentFormat.DOCBOOK)
        if (manpage) add(DocumentFormat.MANPAGE)
    }
}