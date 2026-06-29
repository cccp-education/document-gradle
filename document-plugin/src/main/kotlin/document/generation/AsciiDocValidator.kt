package document.generation

/**
 * Validateur AsciiDoc — fonctions pures, sans LLM ni I/O.
 *
 * Un document AsciiDoc valide commence par un titre de niveau 0 : `= Titre`.
 * Cette regle est la convention minimale du workspace (TAXONOMIE_WORKSPACE.adoc).
 *
 * Utilise par [DocumentGenerationGraph] (nœud `validateAsciiDoc`) pour valider
 * la sortie du LLM avant de l'accepter comme document final.
 */
object AsciiDocValidator {

    private val level0TitleRegex = Regex("(?m)^\\s*=\\s+.+")

    /**
     * Un AsciiDoc est valide s'il contient au moins un titre de niveau 0.
     */
    fun isValid(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        return level0TitleRegex.containsMatchIn(trimmed)
    }
}