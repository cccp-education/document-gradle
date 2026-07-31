package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService

/**
 * Deterministic fake for the whole-article (tree) translation mode.
 * Appends [suffix] to each `N: text` line while preserving the numbering,
 * so [TreeTranslationAdapter.parseNumberedLines] can round-trip it.
 */
class NumberedFakeTranslationService(
    private val suffix: String = " [EN]",
) : TranslationService {

    override fun translate(request: TranslationRequest): TranslationResult {
        val translated = request.sourceText.lines().joinToString("\n") { line ->
            val m = LINE_PATTERN.matchEntire(line.trim())
            if (m != null) {
                val n = m.groupValues[1]
                val text = m.groupValues[2].trim()
                "$n: $text$suffix"
            } else {
                line
            }
        }
        return TranslationResult.Success(translated)
    }

    companion object {
        private val LINE_PATTERN = Regex("""^(\d+):\s*(.+)$""")
    }
}
