package document.translation

import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentTranslationStructureGuardTest {
    @TempDir
    lateinit var tempDir: File

    private val parser = AsciiDocParser()

    private val sourceText =
        """title=Titre
date=2026-01-01
type=post
status=published
~~~~~~

== Introduction

Bonjour le monde.

== Conclusion

Fin.
"""

    private fun sourceFile(): File {
        val source = tempDir.resolve("article.adoc")
        source.writeText(sourceText)
        return source
    }

    private fun targetFile(): File = tempDir.resolve("article-en.adoc")

    private class NewlineHeadingService : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            if (request.sourceText == "Introduction") {
                TranslationResult.Success("Introduction\n== Injected")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
    }

    private class NewlineParagraphService : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            if (request.sourceText == "Bonjour le monde.") {
                TranslationResult.Success("Bonjour\n== Injected")
            } else {
                TranslationResult.Success("${request.sourceText} [EN]")
            }
    }

    private class DroppingRenderer : ArticleRenderer {
        private val delegate = AsciiDocRenderer()

        override fun render(article: PivotArticle): String = delegate.render(article.copy(blocks = article.blocks.drop(1)))
    }

    private class DuplicatingRenderer : ArticleRenderer {
        private val delegate = AsciiDocRenderer()

        override fun render(article: PivotArticle): String = delegate.render(article) + "\n" + delegate.renderBlocks(article.blocks)
    }

    @Test
    fun `a translated heading with a newline keeps the source heading count`() {
        val source = sourceFile()
        val target = targetFile()
        val service = ContentTranslationService(NewlineHeadingService(), parser, AsciiDocRenderer())

        val statuses = service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")

        assertTrue(target.exists())
        val sourceHeadings = parser.parse(sourceText).blocks.count { it is PivotBlock.Heading }
        val renderedHeadings = parser.parse(target.readText()).blocks.count { it is PivotBlock.Heading }
        assertEquals(sourceHeadings, renderedHeadings)
        assertFalse(target.readText().lines().any { it.trim() == "== Injected" })
        assertTrue(statuses.values.all { it.hash.isNotBlank() })
    }

    @Test
    fun `a translated paragraph cannot introduce a heading block`() {
        val source = sourceFile()
        val target = targetFile()
        val service = ContentTranslationService(NewlineParagraphService(), parser, AsciiDocRenderer())

        service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")

        assertTrue(target.exists())
        val sourceBlocks = parser.parse(sourceText).blocks
        val renderedBlocks = parser.parse(target.readText()).blocks
        assertEquals(
            DocumentTranslator.structureSignature(sourceBlocks),
            DocumentTranslator.structureSignature(renderedBlocks),
        )
        assertFalse(target.readText().lines().any { it.trim() == "== Injected" })
    }

    @Test
    fun `a renderer that drops a block is rejected and the target is untouched`() {
        val source = sourceFile()
        val target = targetFile()
        val service =
            ContentTranslationService(
                translationService = NewlineHeadingService(),
                parser = parser,
                renderer = DroppingRenderer(),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")
            }

        assertTrue(exception.message.orEmpty().contains("block structure"))
        assertTrue(exception.message.orEmpty().contains(source.path))
        assertFalse(target.exists(), "the structurally broken artefact must not be written")
    }

    @Test
    fun `translateSingleFile rejects a dropped block and leaves the source untouched`() {
        val langDir = tempDir
        val source = sourceFile()
        val original = source.readText()
        val service =
            ContentTranslationService(
                translationService = NewlineHeadingService(),
                parser = parser,
                renderer = DroppingRenderer(),
            )

        val result = service.translateFiles(listOf(source), langDir, "fr", "en")

        assertFalse(result.success)
        assertTrue(result.errors.any { it.contains("block structure") })
        assertEquals(original, source.readText(), "the in-place source must not be corrupted")
    }

    @Test
    fun `a renderer that duplicates a block is rejected and the target is untouched`() {
        val source = sourceFile()
        val target = targetFile()
        val service =
            ContentTranslationService(
                translationService = NewlineHeadingService(),
                parser = parser,
                renderer = DuplicatingRenderer(),
            )

        val exception =
            assertFailsWith<IllegalStateException> {
                service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")
            }

        assertTrue(exception.message.orEmpty().contains("duplicated"))
        assertFalse(target.exists(), "the duplicated artefact must not be written")
    }

    @Test
    fun `a normal translation still writes the target and returns checksums`() {
        val source = sourceFile()
        val target = targetFile()
        val service = ContentTranslationService(FakeTranslationService(" [EN]"), parser, AsciiDocRenderer())

        val statuses = service.translateSingleFileWithBlockDelta(source, target, emptyMap(), "fr", "en")

        assertTrue(target.exists())
        assertTrue(target.readText().contains("[EN]"))
        assertTrue(statuses.isNotEmpty())
        assertTrue(statuses.values.all { it.hash.isNotBlank() })
    }
}
