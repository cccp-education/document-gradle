package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import document.translation.delta.BlockChecksum
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockTranslationStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * DOC-TRANSLATE-RESILIENCE — a block whose LLM call failed must never be frozen
 * as TRANSLATED. The delta preserves a TRANSLATED block whose source hash is
 * unchanged, so promoting a silent French fallback to TRANSLATED makes the
 * failure permanent (the batch reports 0 errors without ever converging).
 */
class TranslationResilienceTest {

    private val parser = AsciiDocParser()
    private val renderer = AsciiDocRenderer()

    @TempDir
    lateinit var tempDir: File

    private fun sourceArticle(): String = """= Intro

== Heading One

First paragraph.

== Heading Two

Second paragraph.
"""

    @Test
    fun `a block whose LLM call fails is stored PENDING not TRANSLATED`() {
        val service = FlakyTranslationService { it == "Second paragraph." }
        val contentService = ContentTranslationService(service, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val sourceFile = tempDir.resolve("source.adoc").apply { writeText(sourceArticle()) }
        val targetFile = tempDir.resolve("target.adoc")

        val checksums = contentService.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, emptyMap(), "fr", "en"
        )

        // index 1 = first paragraph (translated), index 3 = second paragraph (failed)
        assertEquals(BlockTranslationStatus.TRANSLATED, checksums.getValue("1").status)
        assertEquals(BlockTranslationStatus.PENDING, checksums.getValue("3").status)
        // the silent fallback is still emitted (the rendered article stays complete)
        assertTrue(targetFile.readText().contains("Second paragraph."))
    }

    @Test
    fun `a fully successful translation stores every block TRANSLATED`() {
        val service = FlakyTranslationService { false }
        val contentService = ContentTranslationService(service, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val sourceFile = tempDir.resolve("source.adoc").apply { writeText(sourceArticle()) }
        val targetFile = tempDir.resolve("target.adoc")

        val checksums = contentService.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, emptyMap(), "fr", "en"
        )

        assertTrue(checksums.values.all { it.status == BlockTranslationStatus.TRANSLATED })
        assertTrue(targetFile.readText().contains("Second paragraph. [EN]"))
    }

    @Test
    fun `a PENDING block is re-attempted on the next run and converges`() {
        val failing = FlakyTranslationService { it == "Second paragraph." }
        val firstService = ContentTranslationService(failing, parser, renderer, jbakeRenderer = JbakeNativeRenderer())
        val sourceFile = tempDir.resolve("source.adoc").apply { writeText(sourceArticle()) }
        val targetFile = tempDir.resolve("target.adoc")

        val firstRun = firstService.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, emptyMap(), "fr", "en"
        )
        assertEquals(BlockTranslationStatus.PENDING, firstRun.getValue("3").status)

        // The pool recovered: same source, same hashes.
        val recovered = ContentTranslationService(
            FlakyTranslationService { false }, parser, renderer, jbakeRenderer = JbakeNativeRenderer()
        )
        val secondRun = recovered.translateSingleFileWithBlockDelta(
            sourceFile, targetFile, firstRun, "fr", "en"
        )

        assertEquals(BlockTranslationStatus.TRANSLATED, secondRun.getValue("3").status)
        assertTrue(targetFile.readText().contains("Second paragraph. [EN]"))
    }

    @Test
    fun `translateArticleWithDelta marks the failing block index PENDING`() {
        val sourceArticle = parser.parse(sourceArticle())
        val previousTranslated = parser.parse(sourceArticle())
        val currentHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val previousEntries = currentHashes.mapValues {
            BlockChecksumEntry(it.value, BlockTranslationStatus.TRANSLATED)
        }
        // force index 1 (first paragraph) to be re-translated
        val previous = previousEntries.toMutableMap()
        previous["1"] = BlockChecksumEntry("different", BlockTranslationStatus.TRANSLATED)
        val delta = document.translation.delta.BlockDelta.compute(previous, currentHashes)

        val failing = FlakyTranslationService { it == "First paragraph." }
        val translator = DocumentTranslator(failing)
        translator.translateArticleWithDelta(sourceArticle, previousTranslated, delta, "fr", "en")

        assertTrue("1" in translator.translationFailures)
    }

    @Test
    fun `resolveStatuses promotes every non-failed block to TRANSLATED`() {
        val hashes = mapOf("0" to "h0", "1" to "h1", "2" to "h2")
        val statuses = BlockChecksum.resolveStatuses(hashes, setOf("1"))

        assertEquals(BlockTranslationStatus.TRANSLATED, statuses.getValue("0").status)
        assertEquals(BlockTranslationStatus.PENDING, statuses.getValue("1").status)
        assertEquals(BlockTranslationStatus.TRANSLATED, statuses.getValue("2").status)
        assertEquals("h1", statuses.getValue("1").hash)
    }

    @Test
    fun `resolveStatuses with no failures is all TRANSLATED`() {
        val hashes = mapOf("0" to "h0", "1" to "h1")
        val statuses = BlockChecksum.resolveStatuses(hashes, emptySet())
        assertTrue(statuses.values.all { it.status == BlockTranslationStatus.TRANSLATED })
    }

    private class FlakyTranslationService(
        private val failFor: (String) -> Boolean
    ) : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            if (failFor(request.sourceText)) {
                TranslationResult.Failure("LLM quota exceeded")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
    }
}
