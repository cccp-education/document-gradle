package document.xref

/**
 * Outcome of a cross-reference validation over an AsciiDoc document.
 *
 * Ink Economy Law: the result is a pure immutable value, fully determined by the
 * document text — no I/O, no Gradle dependency, fully unit-testable in isolation.
 */
sealed class XrefValidationResult {
    /** Every referenced anchor resolves to a defined anchor. */
    data object Valid : XrefValidationResult()

    /**
     * At least one referenced anchor has no matching definition.
     *
     * @param missing the sorted list of unresolved reference ids (deduplicated)
     */
    data class Invalid(val missing: List<String>) : XrefValidationResult()
}
