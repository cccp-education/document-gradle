package document.pdf

/**
 * Sealed result of a PDF structural validation (DOC-PDF-CHECK) — mirror of
 * [document.epub.EpubValidationResult].
 *
 * - [Valid] : the file loaded and every page carries extractable text.
 * - [Invalid] : the file is missing, corrupt, empty, or has textless pages;
 *   [issues] carries the sorted, deduplicated finding list.
 */
sealed class PdfValidationResult {

    /** The finding list attached to a result (empty for [Valid]). */
    abstract val issues: List<String>

    /** The PDF passed every structural check. */
    object Valid : PdfValidationResult() {
        override val issues: List<String> = emptyList()
    }

    /** The PDF failed one or more structural checks. */
    data class Invalid(
        override val issues: List<String>,
    ) : PdfValidationResult()
}