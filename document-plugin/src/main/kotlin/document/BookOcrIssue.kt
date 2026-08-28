package document

/**
 * A single OCR / LLM-vision reading failure, localised for human iteration.
 *
 * Carrying the owning [sectionRef] / [sectionTitle] (resolved against the parsed
 * table of contents) lets a human jump straight to "page 73 — section 2.1.1
 * (Numérique et chronobiologie)" instead of hunting through the raw scans.
 *
 * Ink Economy Law: immutable value object, no I/O, fully unit-testable.
 */
data class BookOcrIssue(
    val page: Int,
    val file: String,
    val sectionRef: String?,
    val sectionTitle: String?,
    val reason: OcrFailureReason,
)
