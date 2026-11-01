package document.translation

/**
 * Contract for translating a whole AsciiDoc document.
 *
 * Two strategies implement it:
 *  - [DocumentTranslator]  : leaf-by-leaf (one LLM call per translatable fragment)
 *  - [TreeTranslationAdapter] : whole-article tree (one LLM call per document, YAML round-trip)
 */
interface ArticleTranslator {
    fun translate(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): String
}

/**
 * Capability of a translator that also **returns** the diagnostics of the pass
 * (XF-FP3-3).
 *
 * Callers that need the findings depend on this capability, never on a concrete
 * implementation: [BatchDocumentTranslator] used to test
 * `documentTranslator is DocumentTranslator` (a class check), which coupled the
 * adapter to the leaf-by-leaf strategy. [TreeTranslationAdapter] does not
 * implement it (it produces no per-block diagnostics), so its callers keep the
 * plain [ArticleTranslator] contract.
 */
interface TracedArticleTranslator : ArticleTranslator {
    fun translateOutcome(
        asciidoc: String,
        sourceLanguage: String,
        targetLanguage: String,
    ): DocumentTranslation
}
