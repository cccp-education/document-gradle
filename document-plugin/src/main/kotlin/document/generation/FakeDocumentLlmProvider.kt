package document.generation

/**
 * Provider LLM fake pour les tests unitaires — aucune cle API, aucun reseau.
 *
 * Miroir du pattern codebase (FakeLlmProvider dans Queens).
 * Retourne une reponse AsciiDoc predefinie ou parametree par test.
 */
class FakeDocumentLlmProvider(
    private val response: String = DEFAULT_RESPONSE,
) : DocumentLlmProvider {

    override suspend fun call(prompt: String): String = response

    companion object {
        val DEFAULT_RESPONSE = """
            = Document Genere

            == Introduction

            Contenu genere par le LLM fake pour les tests.
        """.trimIndent()
    }
}