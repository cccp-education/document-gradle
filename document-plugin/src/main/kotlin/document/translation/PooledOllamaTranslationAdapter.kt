package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import dev.langchain4j.model.ollama.OllamaChatModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class PooledOllamaTranslationAdapter(
    internal val baseUrls: List<String>,
    internal val model: String = DEFAULT_MODEL,
    internal val timeout: Duration = Duration.ofMinutes(5),
) : TranslationService {

    private val log = LoggerFactory.getLogger(PooledOllamaTranslationAdapter::class.java)
    private val index = AtomicInteger(0)

    private val chatModels by lazy {
        log.info("[PooledOllamaTranslationAdapter] Creating {} OllamaChatModel(s) for model={}", baseUrls.size, model)
        baseUrls.map { baseUrl ->
            OllamaChatModel.builder()
                .baseUrl(baseUrl)
                .modelName(model)
                .timeout(timeout)
                .build()
        }
    }

    override fun translate(request: TranslationRequest): TranslationResult {
        val prompt = buildPrompt(request)
        val startIdx = index.getAndIncrement() % chatModels.size
        var lastError: Exception? = null

        for (offset in 0 until chatModels.size) {
            val idx = (startIdx + offset) % chatModels.size
            val chatModel = chatModels[idx]
            val baseUrl = baseUrls[idx]
            try {
                val raw = kotlinx.coroutines.runBlocking {
                    withContext(Dispatchers.IO) { chatModel.chat(prompt) }
                }
                val cleaned = raw.trim().trim('"', '\u00AB', '\u00BB', '`', '\n')
                if (cleaned.isBlank()) {
                    log.warn("[PooledOllamaTranslationAdapter] {} returned blank response, trying next", baseUrl)
                    lastError = RuntimeException("Blank response from $baseUrl")
                    continue
                }
                return TranslationResult.Success(cleaned)
            } catch (e: Exception) {
                log.warn("[PooledOllamaTranslationAdapter] {} failed: {}, trying next", baseUrl, e.message)
                lastError = e
            }
        }
        val reason = lastError?.message ?: "All ${chatModels.size} endpoints failed"
        log.error("[PooledOllamaTranslationAdapter] All endpoints exhausted: {}", reason)
        return TranslationResult.Failure(reason)
    }

    private fun buildPrompt(request: TranslationRequest): String =
        """You are a professional translator. Translate the following text from ${request.sourceLanguage} to ${request.targetLanguage}.
Output only the translated text — no explanation, no commentary, no alternatives, no options.

Text to translate:
${request.sourceText}"""

    companion object {
        const val DEFAULT_MODEL = "gemma4:31b-cloud"
        val DEFAULT_PORT_RANGE = 11437..11465

        fun create(
            portRange: IntRange = DEFAULT_PORT_RANGE,
            model: String = DEFAULT_MODEL,
            timeout: Duration = Duration.ofMinutes(5),
        ): PooledOllamaTranslationAdapter {
            val urls = portRange.map { port -> "http://localhost:$port" }
            return PooledOllamaTranslationAdapter(urls, model, timeout)
        }
    }
}
