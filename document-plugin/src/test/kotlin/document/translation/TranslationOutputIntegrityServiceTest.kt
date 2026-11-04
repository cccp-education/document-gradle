package document.translation

import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * DOC-OUT-2 — the service must never write a structurally duplicated artefact.
 * The duplication is produced by a renderer that emits the body twice (the
 * failure mode measured on cheroliv.com), and the guard fails before the write.
 */
class TranslationOutputIntegrityServiceTest {
    @TempDir
    lateinit var tempDir: File

    private class DuplicatingRenderer : ArticleRenderer {
        private val delegate = AsciiDocRenderer()

        override fun render(article: PivotArticle): String = delegate.render(article) + "\n" + delegate.renderBlocks(article.blocks)
    }

    private val source: File
        get() = tempDir.resolve("article.adoc")

    private fun writeSource() {
        source.writeText(
            """
            title=Titre
            date=2026-01-01
            type=post
            status=published
            ~~~~~~

            == Section

            Bonjour le monde.

            Deuxieme paragraphe.
            """.trimIndent(),
        )
    }

    @Test
    fun `a duplicated translation is rejected before the artefact is written`() {
        writeSource()
        val target = tempDir.resolve("article-en.adoc")
        val service =
            ContentTranslationService(
                translationService = FakeTranslationService(" [EN]"),
                renderer = DuplicatingRenderer(),
            )

        val exception =
            assertFailsWith<TranslationException> {
                service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")
            }

        assertTrue(exception.message.orEmpty().contains("duplicated"))
        assertTrue(!target.exists(), "the duplicated artefact must not be written")
    }

    @Test
    fun `a second pass with an unchanged source is idempotent`() {
        writeSource()
        val target = tempDir.resolve("article-en.adoc")
        val service = ContentTranslationService(translationService = FakeTranslationService(" [EN]"))

        val firstChecksums = service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")
        val firstContent = target.readText()

        val secondChecksums = service.translateSingleFileWithBlockDelta(source, target, firstChecksums, "fr", "en")

        assertTrue(firstContent == target.readText(), "the second pass must not append duplicated blocks")
        assertTrue(firstChecksums.keys == secondChecksums.keys)
    }

    @Test
    fun `a normal translation writes the translated artefact`() {
        writeSource()
        val target = tempDir.resolve("article-en.adoc")
        val service = ContentTranslationService(translationService = FakeTranslationService(" [EN]"))

        service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")

        assertTrue(target.exists())
        assertTrue(target.readText().contains("[EN]"))
    }
}
