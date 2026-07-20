package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration

class OllamaTranslationAdapter(
    private val baseUrl: String = System.getenv("OLLAMA_BASE_URL") ?: DEFAULT_BASE_URL,
    private val model: String = System.getenv("OLLAMA_MODEL") ?: DEFAULT_MODEL,
    private val timeout: Duration = Duration.ofMinutes(5),
) : TranslationService {

    private val log = LoggerFactory.getLogger(OllamaTranslationAdapter::class.java)

    private val chatModel by lazy {
        log.info("[OllamaTranslationAdapter] Creating OllamaChatModel: baseUrl={}, model={}", baseUrl, model)
        OllamaChatModel.builder()
            .baseUrl(baseUrl)
            .modelName(model)
            .timeout(timeout)
            .build()
    }

    override fun translate(request: TranslationRequest): TranslationResult {
        val prompt = buildPrompt(request)
        return try {
            val raw = kotlinx.coroutines.runBlocking {
                withContext(Dispatchers.IO) { chatModel.chat(prompt) }
            }
            val cleaned = raw.trim().trim('"', '\u00AB', '\u00BB', '`', '\n')
            if (cleaned.isBlank()) {
                TranslationResult.Failure("LLM returned blank response")
            } else {
                TranslationResult.Success(cleaned)
            }
        } catch (e: Exception) {
            log.warn("[OllamaTranslationAdapter] LLM call failed: {}", e.message)
            TranslationResult.Failure(e.message ?: "LLM call failed")
        }
    }

    private fun buildPrompt(request: TranslationRequest): String =
        """You are a professional translator. Translate the following text from ${request.sourceLanguage} to ${request.targetLanguage}.
Output only the translated text — no explanation, no commentary, no alternatives, no options.

Text to translate:
${request.sourceText}"""

    companion object {
        const val DEFAULT_BASE_URL = "http://localhost:11437"
        const val DEFAULT_MODEL = "gemma4:31b-cloud"
    }
}
