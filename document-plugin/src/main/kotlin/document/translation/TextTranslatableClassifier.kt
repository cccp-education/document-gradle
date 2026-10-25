package document.translation

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
     * A technical fragment is *short*. The unanchored `\d+\s*Go` pattern matched
     * anywhere in a sentence, so an ordinary French paragraph mentioning
     * `120+ Go de VRAM` (cheroliv.com 0119) was classified non-translatable and
     * shipped in French in every language. A sentence is prose, never a
     * disk-size label.
     */
    private const val MAX_TECHNICAL_WORDS = 6

    private fun wordCount(text: String): Int = text.split(Regex("\\s+")).count { it.isNotBlank() }

    fun isTranslatable(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return true
        if (!trimmed.any { it.isLetterOrDigit() }) {
            if (trimmed.matches(Regex("^[,\\s]*$"))) return false
            if (trimmed == "-") return false
            return true
        }
        if (trimmed.matches(Regex("^\\d+$"))) return false
        if (trimmed == "-") return false
        if (trimmed == "reste") return false

        if (TECHNICAL_PATTERNS.any { it.containsMatchIn(trimmed) } && wordCount(trimmed) <= MAX_TECHNICAL_WORDS) {
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
