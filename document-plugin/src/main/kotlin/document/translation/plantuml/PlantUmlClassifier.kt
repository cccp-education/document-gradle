package document.translation.plantuml

sealed interface PlantUmlStrategy {
    data object TranslateLabels : PlantUmlStrategy
    data object PreserveTechnical : PlantUmlStrategy
    data object BorrowVocabulary : PlantUmlStrategy
}

data class PlantUmlBlock(
    val raw: String,
    val borrowedVocabulary: Set<String> = emptySet()
) {
    private val labelRegex = Regex("\"([^\"]+)\"")

    private val technicalIdentifierRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*\\.[a-zA-Z0-9_.]+$")

    /**
     * CHE-I18N-22 US-8 — PlantUML *directives* whose text is not quoted, so the
     * `"…"` label regex never caught them: `title`, `header`, `footer`,
     * `caption`. A diagram can be entirely labelled by a quoted class but keep
     * its title in French (21 cheroliv.com articles at S-044). The directive
     * value is captured up to the end of line.
     */
    private val directiveRegex =
        Regex("""(?m)^\s*(?:title|header|footer|caption)\s+(.+?)\s*$""")

    fun labels(): List<String> {
        val quoted =
            labelRegex.findAll(raw)
                .map { it.groupValues[1] }
                .filter { label ->
                    !technicalIdentifierRegex.matches(label) &&
                        label.any { it.isLetter() }
                }
        val directives =
            directiveRegex.findAll(raw)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotEmpty() && it.any { ch -> ch.isLetter() } }
        return (quoted + directives).distinct().toList()
    }

    internal fun hasBorrowedVocabulary(): Boolean {
        return borrowedVocabulary.any { vocab -> raw.contains("\"$vocab\"") }
    }

    internal fun hasTranslatableLabels(): Boolean = labels().isNotEmpty()
}

class PlantUmlClassifier {

    fun classify(block: PlantUmlBlock): PlantUmlStrategy {
        return when {
            block.hasBorrowedVocabulary() -> PlantUmlStrategy.BorrowVocabulary
            block.hasTranslatableLabels() -> PlantUmlStrategy.TranslateLabels
            else -> PlantUmlStrategy.PreserveTechnical
        }
    }
}
