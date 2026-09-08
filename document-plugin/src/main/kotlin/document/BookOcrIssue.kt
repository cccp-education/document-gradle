package document

/**
 * A single OCR / LLM-vision reading failure, localised for human iteration.
 *
 * Carrying the owning [sectionRef] / [sectionTitle] (resolved against the parsed
 * table of contents) lets a human jump straight to "page 73 — section 2.1.1
 * (Numérique et chronobiologie)" instead of hunting through the raw scans.
 *
 * [detail] carries the discriminating evidence for the structured reasons
 * (the missing image path for [OcrFailureReason.IMAGE_MISSING], the suspect
 * table row for [OcrFailureReason.TABLE_SUSPECT]); `null` for the whole-page
 * reasons (ILLISIBLE, TOO_SHORT) which need no per-finding context. Backward
 * compatible: existing ILLISIBLE / TOO_SHORT issues keep `detail = null`.
 *
 * Ink Economy Law: immutable value object, no I/O, fully unit-testable.
 */
data class BookOcrIssue(
    val page: Int,
    val file: String,
    val sectionRef: String?,
    val sectionTitle: String?,
    val reason: OcrFailureReason,
    val detail: String? = null,
)
