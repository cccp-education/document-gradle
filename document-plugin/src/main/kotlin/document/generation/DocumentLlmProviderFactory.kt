package document.generation

/**
 * Factory de [DocumentLlmProvider] — selection selon le mode configure.
 *
 * Modes :
 * - "ollama" (defaut, production) : [OllamaDocumentLlmProvider] (langchain4j, reseau local).
 * - "fake" (tests, sans reseau) : [FakeDocumentLlmProvider] (reponse predefinie).
 *
 * Permet aux tests fonctionnels (GradleTestKit) d'invoquer `generateDocument`
 * avec le LLM fake sans cle API ni Ollama en cours d'execution.
 */
object DocumentLlmProviderFactory {

    fun create(mode: String = "ollama"): DocumentLlmProvider = when (mode.lowercase()) {
        "fake" -> FakeDocumentLlmProvider()
        "ollama" -> OllamaDocumentLlmProvider()
        else -> throw IllegalArgumentException("Unknown llmMode: '$mode' — expected 'ollama' or 'fake'")
    }
}