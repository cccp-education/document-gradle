package document

/**
 * Reason why an OCR / LLM-vision pass failed to read a physical page.
 *
 * - [ILLISIBLE]: the vision model emitted the `[ILLISIBLE]` marker, meaning it
 *   could not decipher the page content (handwriting, scan artefact, ...).
 * - [TOO_SHORT]: the page body is empty or truncated below a sane threshold,
 *   indicating a dropped or partial OCR capture.
 */
enum class OcrFailureReason {
    ILLISIBLE,
    TOO_SHORT,
}
