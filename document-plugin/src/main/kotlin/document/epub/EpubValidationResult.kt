package document.epub

/**
 * Sealed result of an EPUB validation (DOC-EPUBCHECK).
 *
 * - [Valid] : the file passed epubcheck with zero errors/warnings/fatals.
 * - [Invalid] : the file has issues; carries the sorted, deduplicated message
 *   list (format `ID severity: text`).
 */
sealed class EpubValidationResult {

    /** The issue list attached to a result (empty for [Valid]). */
    abstract val issues: List<String>

    /** The file passed epubcheck. */
    object Valid : EpubValidationResult() {
        override val issues: List<String> = emptyList()
    }

    /** The file failed epubcheck; [issues] carries the epubcheck messages. */
    data class Invalid(
        override val issues: List<String>,
    ) : EpubValidationResult()
}