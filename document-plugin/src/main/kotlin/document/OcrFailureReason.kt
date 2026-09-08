package document

/**
 * Reason why an OCR / LLM-vision pass failed to read a physical page.
 *
 * - [ILLISIBLE]: the vision model emitted the `[ILLISIBLE]` marker, meaning it
 *   could not decipher the page content (handwriting, scan artefact, ...).
 * - [TOO_SHORT]: the page body is empty or truncated below a sane threshold,
 *   indicating a dropped or partial OCR capture.
 * - [IMAGE_MISSING]: the page references an `image::` directive whose target
 *   file does not exist next to the scan — a "ghost image" named by the
 *   vision model but never captured (blocks EPUB navigation, RSC-007).
 * - [TABLE_SUSPECT]: the page carries a linearised table row (pipe-separated
 *   fragments) whose final cells were likely lost by the OCR pass.
 * - [STRUCT_SUSPECT]: the page body carries a structural doubt marker emitted
 *   by the vision model (`invalid part`, `out of sequence`) — the hierarchy
 *   the model produced is uncertain.
 */
enum class OcrFailureReason {
    ILLISIBLE,
    TOO_SHORT,
    IMAGE_MISSING,
    TABLE_SUSPECT,
    STRUCT_SUSPECT,
}
