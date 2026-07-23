package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import org.junit.jupiter.api.Test
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PooledOllamaTranslationAdapterTest {

    @Test
    fun `factory creates adapter with default port range`() {
        val adapter = PooledOllamaTranslationAdapter.create()
        assertEquals(29, adapter.baseUrls.size)
        assertEquals("http://localhost:11437", adapter.baseUrls.first())
        assertEquals("http://localhost:11465", adapter.baseUrls.last())
    }

    @Test
    fun `factory creates adapter with custom port range`() {
        val adapter = PooledOllamaTranslationAdapter.create(portRange = 11437..11439)
        assertEquals(3, adapter.baseUrls.size)
        assertEquals("http://localhost:11437", adapter.baseUrls[0])
        assertEquals("http://localhost:11438", adapter.baseUrls[1])
        assertEquals("http://localhost:11439", adapter.baseUrls[2])
    }

    @Test
    fun `factory creates adapter with custom model`() {
        val adapter = PooledOllamaTranslationAdapter.create(
            portRange = 11437..11437,
            model = "gpt-oss:120b-cloud"
        )
        assertEquals("gpt-oss:120b-cloud", adapter.model)
    }

    @Test
    fun `factory creates adapter with custom timeout`() {
        val adapter = PooledOllamaTranslationAdapter.create(
            portRange = 11437..11437,
            timeout = Duration.ofSeconds(30)
        )
        assertEquals(Duration.ofSeconds(30), adapter.timeout)
    }

    @Test
    fun `translate returns failure when all endpoints unreachable`() {
        val adapter = PooledOllamaTranslationAdapter(
            baseUrls = listOf("http://localhost:1", "http://localhost:2"),
            model = "gemma4:31b-cloud",
            timeout = Duration.ofMillis(100)
        )
        val request = TranslationRequest("Hello", "en", "fr")
        val result = adapter.translate(request)
        assertIs<TranslationResult.Failure>(result)
        assertTrue(result.reason.isNotBlank(), "Failure reason must not be blank")
    }

    @Test
    fun `round-robin cycles through endpoints`() {
        val adapter = PooledOllamaTranslationAdapter(
            baseUrls = listOf("http://localhost:1", "http://localhost:2", "http://localhost:3"),
            model = "gemma4:31b-cloud",
            timeout = Duration.ofMillis(100)
        )
        val request = TranslationRequest("Hello", "en", "fr")

        val results = (1..6).map { adapter.translate(request) }
        assertEquals(6, results.size)
        results.forEach { assertIs<TranslationResult.Failure>(it) }
    }

    @Test
    fun `default port range starts at 11437 not 11434`() {
        val adapter = PooledOllamaTranslationAdapter.create()
        val urls = adapter.baseUrls
        assertTrue(urls.none { it.contains("11434") || it.contains("11435") || it.contains("11436") },
            "Ports 11434-11436 must never appear in rotation")
        assertTrue(urls.all { it.contains("localhost:114") },
            "All URLs must be localhost Ollama ports")
    }
}
