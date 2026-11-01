package document.translation

import com.fasterxml.jackson.databind.ObjectMapper
import document.translation.validation.TableValidationResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * XF-FP3-3 — [BatchDocumentTranslator] consumes a **capability**
 * ([TracedArticleTranslator]), never a concrete implementation.
 *
 * Regression: it used to test `documentTranslator is DocumentTranslator` and then
 * read the translator's mutable properties. The capability makes the dependency
 * explicit (DIP) and lets any translator report diagnostics — here a fake that is
 * *not* a [DocumentTranslator].
 */
class BatchDocumentTranslatorTraceTest {
    @TempDir
    lateinit var tempDir: File

    private val invalidTable =
        TableValidationResult.Invalid(
            articleTitle = "Fake",
            tableIndex = 0,
            reason = "cell [0,0] contains table delimiter '|==='",
        )

    /** A translator that is *not* a [DocumentTranslator] but reports a trace. */
    private class FakeTracedTranslator(
        private val trace: TranslationTrace,
    ) : TracedArticleTranslator {
        override fun translate(
            asciidoc: String,
            sourceLanguage: String,
            targetLanguage: String,
        ): String = asciidoc

        override fun translateOutcome(
            asciidoc: String,
            sourceLanguage: String,
            targetLanguage: String,
        ): DocumentTranslation = DocumentTranslation(asciidoc, trace)
    }

    /** A translator without the capability: no report must be produced. */
    private class PlainTranslator : ArticleTranslator {
        override fun translate(
            asciidoc: String,
            sourceLanguage: String,
            targetLanguage: String,
        ): String = asciidoc
    }

    private fun writeSource(): File =
        tempDir.resolve("source").apply { mkdirs() }.resolve("page.adoc").apply {
            writeText("= Page\n\n== Section\n\nSome text.\n")
        }

    private fun runBatch(translator: ArticleTranslator): File {
        val outputDir = tempDir.resolve("out").apply { mkdirs() }
        BatchDocumentTranslator(translator).translateBatch(
            BatchTranslationRequest(
                sourceDir = tempDir.resolve("source"),
                outputDir = outputDir,
                sourceLanguage = "fr",
                targetLanguage = "en",
            ),
        )
        return outputDir
    }

    @Test
    fun `a traced translator drives the table validation report`() {
        writeSource()
        val outputDir = runBatch(FakeTracedTranslator(TranslationTrace(tableValidationResults = listOf(invalidTable))))

        val report = outputDir.resolve("table-validation-report.json")
        assertTrue(report.isFile, "Expected a report driven by the TracedArticleTranslator capability")
        val entries = ObjectMapper().readTree(report.readText()).get("entries")
        assertEquals(1, entries.size(), "the fake trace must be reported: $entries")
        assertTrue(report.readText().contains("cell [0,0]"), "the finding reason must be in the report")
    }

    @Test
    fun `a plain translator produces no report`() {
        writeSource()
        val outputDir = runBatch(PlainTranslator())

        assertFalse(
            outputDir.resolve("table-validation-report.json").exists(),
            "no capability -> no diagnostics -> no report file",
        )
    }
}
