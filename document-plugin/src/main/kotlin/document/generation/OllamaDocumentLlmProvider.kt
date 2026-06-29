package document.generation

import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Provider LLM Ollama pour la generation AsciiDoc en production (DOC-2).
 *
 * Utilise langchain4j (OllamaChatModel) pour l'execution LLM — koog orchestre.
 *
 * RÈGLE ABSOLUE : pas de cle API, pas de token SSH en clair.
 * Ollama tourne en local (Docker), port 11437-11465 (rotation, AGENTS.adoc).
 * L'auth est geree par le conteneur lui-meme (cle SSH montee en volume).
 *
 * Modèles autorisés : gpt-oss:120b-cloud, gemma4:31b-cloud (AGENTS.adoc).
 * Port par defaut : 11437 (premier port de la plage disponible).
 *
 * Variables d'environnement de surcharge :
 * - OLLAMA_BASE_URL : URL de base Ollama (defaut http://localhost:11437)
 * - OLLAMA_MODEL : nom du modele (defaut gemma4:31b-cloud)
 */
class OllamaDocumentLlmProvider(
    private val baseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: DEFAULT_BASE_URL,
    private val model: String = System.getenv("OLLAMA_MODEL") ?: DEFAULT_MODEL,
    private val timeout: Duration = Duration.ofMinutes(5),
) : DocumentLlmProvider {

    private val log = LoggerFactory.getLogger(OllamaDocumentLlmProvider::class.java)

    private val chatModel by lazy {
        log.info("[OllamaDocumentLlmProvider] Creating OllamaChatModel: baseUrl={}, model={}", baseUrl, model)
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(model)
            .timeout(timeout)
            .build()
    }

    override suspend fun call(prompt: String): String {
        log.info("[OllamaDocumentLlmProvider] Calling Ollama: model={}, promptLength={}", model, prompt.length)
        val response = withContext(Dispatchers.IO) { chatModel.chat(prompt) }
        log.info("[OllamaDocumentLlmProvider] Ollama response received: length={}", response.length)
        return response
    }

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11437"
        const val DEFAULT_MODEL = "gemma4:31b-cloud"
    }
}