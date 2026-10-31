package document.translation

import contracts.i18n.PreserveCatalog

object TextTranslatableClassifier {

    private val TECHNICAL_PATTERNS = listOf(
        Regex("\\d+\\s*Go\\b"),
        Regex("\\d+\\s*Mo\\b"),
        Regex("[a-z]{2}_[A-Z]{2}\\.UTF-\\d+"),
        Regex("\\bext4\\b"),
        Regex("\\bsquashfs\\b"),
        Regex("\\bbs=\\d+[A-Z]"),
        Regex("\\bconv=\\w+"),
        Regex("^,\\s*$"),
        Regex("^\\d+$"),
        Regex("^-$"),
        Regex("^reste$"),
        Regex("~?\\d+\\s*Go(\\s+sur\\s+\\d+\\s*Go)?$")
    )

    private val PACKAGE_LIST_PATTERN = Regex("^[a-z][a-z0-9+.-]*(?:,\\s*[a-z][a-z0-9+.-]*){2,}$")

    /**
     * A technical *fragment* is short and carries no sentence structure. The
     * unanchored `\d+\s*Go` pattern matched anywhere in a sentence, so an ordinary
     * French paragraph mentioning `120+ Go de VRAM` (cheroliv.com 0119) was
     * classified non-translatable and shipped in French in every language. A run
     * carrying French function words is prose, never a disk-size label.
     */
    private val FRENCH_FUNCTION_WORDS =
        setOf(
            "le", "la", "les", "de", "des", "du", "un", "une", "est", "et", "ou",
            "pour", "avec", "dans", "sur", "pas", "qui", "que", "ce", "cette",
            "au", "aux", "il", "elle", "nous", "vous", "on", "par", "plus", "mais",
        )

    private fun isProse(text: String): Boolean {
        val words = text.lowercase().split(Regex("[^\\p{L}]+")).filter { it.isNotBlank() }
        return words.size >= 4 && words.any { it in FRENCH_FUNCTION_WORDS }
    }

    fun isTranslatable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        // MEM-I18N-PRESERVE (N0) — a fragment whose every letter belongs to a
        // preserved marker (macro, fence, inline code, anchor, attribute reference,
        // URL) is markup, never prose: the shared catalog replaces the local
        // re-implementation and can no longer drift from plantuml.
        if (PreserveCatalog.isFullyPreserved(trimmed)) return false
        if (!trimmed.any { it.isLetterOrDigit() }) {
            if (trimmed.matches(Regex("^[,\\s]*$"))) return false
            if (trimmed == "-") return false
            return true
        }
        if (trimmed.matches(Regex("^\\d+$"))) return false
        if (trimmed == "-") return false
        if (trimmed == "reste") return false

        if (!isProse(trimmed) && TECHNICAL_PATTERNS.any { it.containsMatchIn(trimmed) }) {
            return false
        }

        if (PACKAGE_LIST_PATTERN.matches(trimmed)) return false

        val words = trimmed.split(Regex("[\\s,()/:]+"))
            .filter { it.length >= 3 && it.any { c -> c.isLetter() && c.lowercaseChar() in 'a'..'z' && c !in 'A'..'Z' || c.lowercaseChar() in 'a'..'z' } }
        val naturalWords = words.filter { word ->
            word.length >= 3 &&
            word.any { it.isLetter() } &&
            !word.matches(Regex("[A-Z]{1,3}")) &&
            !word.matches(Regex("[a-z]{1,2}\\d.*")) &&
            !TECHNICAL_PATTERNS.any { p -> p.matches(word) }
        }
        return naturalWords.isNotEmpty()
    }
}
