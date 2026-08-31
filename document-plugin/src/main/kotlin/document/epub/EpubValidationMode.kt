package document.epub

/**
 * Severity of the EPUB validation gate (DOC-EPUBCHECK). Mirrors
 * [document.xref.XrefValidationMode] / [document.validation.HtmlLinkLintMode].
 */
enum class EpubValidationMode {
    /** No-op (default, backward-compatible). */
    OFF,

    /** Log warnings on EPUB issues, write the report, keep the build green. */
    LENIENT,

    /** Fail the build (fail-fast) when the EPUB does not conform. */
    STRICT,
}