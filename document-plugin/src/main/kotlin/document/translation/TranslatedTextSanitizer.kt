package document.translation

/**
 * Makes a translated text fragment safe for **one** AsciiDoc block.
 *
 * A translation is a text replacement: it must never be able to open a new
 * block. LLMs routinely answer with a line break, or with a paragraph whose
 * first characters are an AsciiDoc marker (`==`, `|===`, `[plantuml]`, `----`,
 * …). Rendered at line start, such text is reparsed as a heading, a table, an
 * attribute list or a delimited block — the rendered variant then has a
 * different block signature from its source (measured C6 failures: new
 * `section-count-drift` on `en` and `0108`).
 *
 * Rules:
 *  - CR, LF and CRLF become a single space, then the text is trimmed, so a
 *    translation can never span several lines;
 *  - a **leading** structural marker is escaped with a backslash, which is the
 *    AsciiDoc way to keep the character literal at line start;
 *  - a [PivotInline.LineBreak] is an intentional block-internal break and is
 *    never passed here (it is a separate pivot node).
 *
 * Pure and deterministic: no I/O, no state.
 */
object TranslatedTextSanitizer {
    private val LEADING_DELIMITERS = listOf("----", "....", "++++")

    fun sanitize(text: String): String {
        val singleLine =
            text
                .replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .trim()
        if (singleLine.isEmpty()) return singleLine
        return if (needsEscape(singleLine)) "\\$singleLine" else singleLine
    }

    private fun needsEscape(text: String): Boolean {
        val first = text[0]
        if (first == '=' || first == '|' || first == '[') return true
        return LEADING_DELIMITERS.any { text.startsWith(it) }
    }
}
