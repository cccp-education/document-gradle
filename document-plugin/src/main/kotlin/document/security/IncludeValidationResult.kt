package document.security

/**
 * Outcome of auditing the `include::` directives of an AsciiDoc document
 * against an allowed filesystem root (DOC-CR4).
 *
 * Ubiquitous language : a document is [Valid] when every include resolves
 * inside the allowed root, otherwise the first [Invalid] finding is reported.
 */
sealed interface IncludeValidationResult {

    /** Every include directive stays within the allowed root. */
    data object Valid : IncludeValidationResult

    /**
     * An include directive escapes the allowed root (path traversal) or uses
     * an absolute path.
     *
     * @param reason human-readable cause ("include escapes allowed root" / "absolute path not allowed")
     * @param offendingTarget the include target as written in the source
     * @param line 1-based line number of the offending directive
     */
    data class Invalid(
        val reason: String,
        val offendingTarget: String,
        val line: Int,
    ) : IncludeValidationResult
}
