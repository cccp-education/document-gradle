package document.validation

/**
 * Outcome of an HTML link lint over a rendered document (`convertDocumentToHtml`).
 *
 * Ink Economy Law: the result is a pure immutable value, fully determined by the
 * HTML text — no I/O, no Gradle dependency, fully unit-testable in isolation.
 */
sealed class HtmlLinkLintResult {
    /** Every internal (`href="#id"`) link resolves to a defined anchor (`id`/`name`). */
    data object Valid : HtmlLinkLintResult()

    /**
     * At least one internal link points to an anchor with no matching definition.
     *
     * @param deadLinks the sorted, deduplicated list of unresolved fragment ids
     */
    data class Invalid(val deadLinks: List<String>) : HtmlLinkLintResult()
}
