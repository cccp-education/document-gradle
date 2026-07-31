package document.translation

/**
 * Contract for translating a whole AsciiDoc document.
 *
 * Two strategies implement it:
 *  - [DocumentTranslator]  : leaf-by-leaf (one LLM call per translatable fragment)
 *  - [TreeTranslationAdapter] : whole-article tree (one LLM call per document, YAML round-trip)
 */
interface ArticleTranslator {
    fun translate(asciidoc: String, sourceLanguage: String, targetLanguage: String): String
}
