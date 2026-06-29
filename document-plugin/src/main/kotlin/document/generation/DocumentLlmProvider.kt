package document.generation

/**
 * Abstraction d'appel LLM pour la generation AsciiDoc — Clean Architecture.
 *
 * Permet d'injecter un vrai modele (Ollama local, port 11437-11465) en production
 * et un [FakeDocumentLlmProvider] en test, sans cle API ni reseau.
 *
 * Miroir de `codebase.koog.llm.LlmProvider` (Queens) — contrat suspendu `call(prompt)`.
 * koog orchestre les nœuds du graphe, langchain4j execute l'appel LLM.
 */
fun interface DocumentLlmProvider {
    suspend fun call(prompt: String): String
}