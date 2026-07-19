package document.translation.plantuml

sealed interface PlantUmlStrategy {
    data object TranslateLabels : PlantUmlStrategy
    data object PreserveTechnical : PlantUmlStrategy
    data object BorrowVocabulary : PlantUmlStrategy
}

data class PlantUmlBlock(
    val raw: String
) {
    private val labelRegex = Regex("\"([^\"]+)\"")

    private val technicalIdentifierRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*\\.[a-zA-Z0-9_.]+$")

    private val borrowedVocabulary = setOf("REAC", "AFNOR", "DC", "TS", "RNCP", "CP", "ECF")

    fun labels(): List<String> {
        val matches = labelRegex.findAll(raw).map { it.groupValues[1] }.toList()
        return matches.filter { label ->
            !technicalIdentifierRegex.matches(label) &&
                label.any { it.isLetter() }
        }
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
