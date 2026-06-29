package document.generation

/**
 * Etat immutable du graphe koog de generation AsciiDoc.
 *
 * Ubiquitous language DDD : un [DocumentGenerationState] represente le contexte
 * d'une requete de generation IA — un prompt d'entree, un document AsciiDoc
 * produit (ou une erreur de validation).
 *
 * koog orchestre les transitions de cet etat via `copy()` (State Pattern).
 * langchain4j execute l'appel LLM dans le nœud `callLlm`.
 *
 * @property prompt       La consigne utilisateur (description du document a generer).
 * @property systemPrompt Le system prompt optionnel (conventions AsciiDoc du workspace).
 * @property rawOutput    La sortie brute du LLM (avant validation).
 * @property document     Le document AsciiDoc valide (apres validation) — resultat final.
 * @property error        Message d'erreur si la generation ou la validation echoue.
 */
data class DocumentGenerationState(
    val prompt: String,
    val systemPrompt: String = "",
    val rawOutput: String = "",
    val document: String = "",
    val error: String? = null,
) {
    val isFinished: Boolean get() = document.isNotEmpty() || error != null
}