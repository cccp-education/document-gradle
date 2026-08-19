package document

/**
 * Result of validating a assembled book against its table of contents.
 *
 * [BookValidationResult] is a sealed type with two variants:
 * - [Valid] — all TOC sections have a corresponding non-empty page, no orphan
 *   pages, no duplicates, and all PDF references resolve (when checked).
 * - [Invalid] — one or more validation rules failed. The [reasons] list
 *   carries human-readable descriptions of every failure.
 *
 * Ink Economy Law: the validation is deterministic — the same set of pages
 * and TOC always produces the same result.
 */
sealed class BookValidationResult {

    /**
     * The book passes all validation checks.
     *
     * @property pageCount the number of AsciiDoc pages that were validated
     */
    data class Valid(val pageCount: Int) : BookValidationResult() {

        init {
            require(pageCount >= 0) { "Valid pageCount must be non-negative, got: $pageCount" }
        }
    }

    /**
     * The book fails one or more validation checks.
     *
     * @property reasons the list of human-readable failure descriptions
     */
    data class Invalid(val reasons: List<String>) : BookValidationResult() {

        init {
            require(reasons.isNotEmpty()) { "Invalid must have at least one reason" }
        }

        /**
         * Joins all reasons into a single report string.
         */
        fun report(): String = reasons.joinToString("\n") { "  - $it" }
    }
}