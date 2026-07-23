package document.translation

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BatchDocumentTranslatorTest {

    @TempDir
    lateinit var tempDir: File

    private val fakeService = FakeTranslationService(" [EN]")
    private val translator = BatchDocumentTranslator(DocumentTranslator(fakeService))

    private fun adoc(name: String, content: String): File {
        val file = tempDir.resolve(name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    @Test
    fun `translateBatch translates all adoc files from source dir to output dir`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/article1.adoc", jumble("Article Un", "Texte un en francais"))
        adoc("src/article2.adoc", jumble("Article Deux", "Texte deux en francais"))
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(2, result.count)
        assertTrue(result.success)
        assertTrue(outputDir.resolve("article1.adoc").exists())
        assertTrue(outputDir.resolve("article2.adoc").exists())
        assertTrue(outputDir.resolve("article1.adoc").readText().contains("Article Un [EN]"))
        assertTrue(outputDir.resolve("article2.adoc").readText().contains("Article Deux [EN]"))
    }

    @Test
    fun `translateBatch preserves relative subdirectory structure`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/2019/old.adoc", jumble("Old", "Vieux texte"))
        adoc("src/2026/new.adoc", jumble("New", "Nouveau texte"))
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(2, result.count)
        assertTrue(outputDir.resolve("2019/old.adoc").exists())
        assertTrue(outputDir.resolve("2026/new.adoc").exists())
    }

    @Test
    fun `translateBatch skips excluded relative paths`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/blog/keep.adoc", jumble("Keep", "Garder"))
        adoc("src/draft/skip.adoc", jumble("Skip", "Passer"))
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en", excludeRelativePaths = setOf("draft")),
        )

        assertEquals(1, result.count)
        assertTrue(outputDir.resolve("blog/keep.adoc").exists())
        assertFalse(outputDir.resolve("draft/skip.adoc").exists())
    }

    @Test
    fun `translateBatch only processes adoc files`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/article.adoc", jumble("Adoc", "Texte adoc"))
        tempDir.resolve("src/notes.txt").writeText("not adoc")
        tempDir.resolve("src/image.png").writeText("not adoc")
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(1, result.count)
        assertTrue(outputDir.resolve("article.adoc").exists())
        assertFalse(outputDir.resolve("notes.txt").exists())
    }

    @Test
    fun `translateBatch handles empty source dir`() {
        val sourceDir = tempDir.resolve("empty").apply { mkdirs() }
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(0, result.count)
        assertTrue(result.success)
    }

    @Test
    fun `translateBatch degrades gracefully when LLM fails — source text written, no errors`() {
        val failingService = object : contracts.i18n.TranslationService {
            override fun translate(request: contracts.i18n.TranslationRequest) =
                contracts.i18n.TranslationResult.Failure("LLM down")
        }
        val translatorWithFailure = BatchDocumentTranslator(DocumentTranslator(failingService))
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/a.adoc", jumble("A", "Texte A"))
        adoc("src/b.adoc", jumble("B", "Texte B"))
        val outputDir = tempDir.resolve("out")

        val result = translatorWithFailure.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(2, result.count, "both files written even on LLM failure (graceful degradation)")
        assertFalse(result.hasErrors, "LLM failure is graceful — not a batch error")
        val contentA = outputDir.resolve("a.adoc").readText()
        assertFalse(contentA.contains("[EN]"), "LLM failed so text should be untranslated source")
    }

    @Test
    fun `translateBatch does not modify source files`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        val srcFile = adoc("src/article.adoc", jumble("Article", "Texte original en francais"))
        val originalContent = srcFile.readText()
        val outputDir = tempDir.resolve("out")

        translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(originalContent, srcFile.readText(), "source file must not be modified")
    }

    @Test
    fun `translateBatch translates JBake native articles preserving structure`() {
        val sourceDir = tempDir.resolve("src").apply { mkdirs() }
        adoc("src/jbake.adoc", """= Mon Article JBake
@CherOliv
2026-07-21
:jbake-type: post
:jbake-status: published

Ceci est un article au format JBake native.
""")
        val outputDir = tempDir.resolve("out")

        val result = translator.translateBatch(
            BatchTranslationRequest(sourceDir, outputDir, "fr", "en"),
        )

        assertEquals(1, result.count)
        val content = outputDir.resolve("jbake.adoc").readText()
        assertTrue(content.startsWith("= Mon Article JBake [EN]"))
        assertTrue(content.contains("@CherOliv"))
        assertTrue(content.contains(":jbake-type: post"))
    }

    private fun jumble(title: String, body: String): String =
        """title=$title
date=2026-07-20
type=page
status=published
~~~~~~

== $title

$body
"""
}