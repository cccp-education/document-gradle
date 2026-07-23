package document.translation

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BatchTranslationModelTest {

    @Test
    fun `BatchTranslationRequest holds source dir, output dir, languages, and exclusions`() {
        val source = File("/tmp/source")
        val output = File("/tmp/output")
        val request = BatchTranslationRequest(
            sourceDir = source,
            outputDir = output,
            sourceLanguage = "fr",
            targetLanguage = "en",
            excludeRelativePaths = setOf("draft/"),
        )

        assertEquals(source, request.sourceDir)
        assertEquals(output, request.outputDir)
        assertEquals("fr", request.sourceLanguage)
        assertEquals("en", request.targetLanguage)
        assertEquals(setOf("draft/"), request.excludeRelativePaths)
    }

    @Test
    fun `BatchTranslationRequest defaults to empty exclusions`() {
        val request = BatchTranslationRequest(
            sourceDir = File("/tmp/source"),
            outputDir = File("/tmp/output"),
            sourceLanguage = "fr",
            targetLanguage = "en",
        )

        assertTrue(request.excludeRelativePaths.isEmpty())
    }

    @Test
    fun `BatchTranslationResult holds translated files, errors, and derives success`() {
        val result = BatchTranslationResult(
            translatedFiles = listOf("blog/001.adoc", "blog/002.adoc"),
            errors = emptyList(),
        )

        assertEquals(2, result.count)
        assertTrue(result.success)
        assertFalse(result.hasErrors)
    }

    @Test
    fun `BatchTranslationResult with errors is not success`() {
        val result = BatchTranslationResult(
            translatedFiles = listOf("blog/001.adoc"),
            errors = listOf("blog/002.adoc: LLM unavailable"),
        )

        assertEquals(1, result.count)
        assertEquals(1, result.errors.size)
        assertFalse(result.success)
        assertTrue(result.hasErrors)
    }

    @Test
    fun `BatchTranslationResult empty has zero count and is success`() {
        val result = BatchTranslationResult(
            translatedFiles = emptyList(),
            errors = emptyList(),
        )

        assertEquals(0, result.count)
        assertTrue(result.success)
        assertFalse(result.hasErrors)
    }
}