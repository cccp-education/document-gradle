package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import document.translation.delta.BlockTranslationStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DOC-TRANSLATE-RESILIENCE — functional proof on the real filesystem: the
 * production [ContentTranslationService] runs against real files, the real pivot
 * parser and the real renderer, with a service that fails one block on the first
 * run and recovers on the second. The failed block must be persisted PENDING so
 * the second run re-attempts it — and converges — instead of the delta freezing
 * the silent source-language fallback as TRANSLATED forever.
 */
class TranslationResilienceFunctionalTest {

    @TempDir
    lateinit var tempDir: File

    private val parser = AsciiDocParser()
    private val renderer = AsciiDocRenderer()

    private class ControllableService(
        private val failingPhrase: String,
        var failing: Boolean,
    ) : TranslationService {
        var calls = 0
            private set

        override fun translate(request: TranslationRequest): TranslationResult {
            calls++
            return if (failing && request.sourceText == failingPhrase) {
                TranslationResult.Failure("LLM quota exceeded")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
        }
    }

    @Test
    fun `a failed block is persisted PENDING then converges on the next run`() {
        val sourceFile = tempDir.resolve("source.adoc")
        val targetFile = tempDir.resolve("target.adoc")
        sourceFile.writeText(
            """= Intro

== Section One

First paragraph.

== Section Two

Second paragraph.
"""
        )

        // Run 1 — the LLM fails on the second paragraph.
        val service = ControllableService("Second paragraph.", failing = true)
        val runner = ContentTranslationService(service, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val firstStatuses = runner.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, emptyMap(), "fr", "en"
        )

        val secondParagraphKey = firstStatuses.entries.first { it.value.status == BlockTranslationStatus.PENDING }.key
        assertTrue(firstStatuses.values.any { it.status == BlockTranslationStatus.TRANSLATED })
        // the silent fallback is emitted for the failed block (article stays complete)
        assertTrue(targetFile.readText().contains("Second paragraph."))

        // Run 2 — the pool recovered; same source, same hashes.
        service.failing = false
        val secondStatuses =
            runner.translateSingleFileWithBlockDelta(sourceFile, targetFile, firstStatuses, "fr", "en")

        assertEquals(BlockTranslationStatus.TRANSLATED, secondStatuses.getValue(secondParagraphKey).status)
        assertTrue(secondStatuses.values.all { it.status == BlockTranslationStatus.TRANSLATED })
        assertTrue(targetFile.readText().contains("Second paragraph. [EN]"))
    }

    @Test
    fun `a fully successful run marks every block TRANSLATED`() {
        val sourceFile = tempDir.resolve("source.adoc")
        val targetFile = tempDir.resolve("target.adoc")
        sourceFile.writeText(
            """= Intro

== Section One

First paragraph.

== Section Two

Second paragraph.
"""
        )

        val service = ControllableService("never", failing = false)
        val runner = ContentTranslationService(service, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val statuses = runner.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, emptyMap(), "fr", "en"
        )

        assertTrue(statuses.isNotEmpty())
        assertTrue(statuses.values.all { it.status == BlockTranslationStatus.TRANSLATED })
        assertTrue(targetFile.readText().contains("Second paragraph. [EN]"))
    }
}
