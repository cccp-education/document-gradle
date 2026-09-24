package document

/**
 * Selects how a [MatterPolicy] is resolved for a book (DOC-BOOK-MATTER).
 *
 * This is the Gradle-facing knob: the DSL/CLI exposes a [MatterPolicyMode] and
 * the book domain resolves it against the real table of contents.
 *
 * - [DERIVED] — adopt the convention roots **only when the TOC declares them**
 *   ([MatterPolicy.derive]). A body-only TOC then requires no matter, which
 *   removes the permanent false positive of rule S3. This is the sensible
 *   default for the scanned-content pipeline.
 * - [LEGACY] — keep the historical `0` / `9` convention whatever the TOC is
 *   ([MatterPolicy.DEFAULT]). Backward compatible with pre-DOC-BOOK-MATTER
 *   behaviour.
 * - [NONE] — require no matter at all ([MatterPolicy.NONE]).
 */
enum class MatterPolicyMode {
    DERIVED,
    LEGACY,
    NONE,
}
