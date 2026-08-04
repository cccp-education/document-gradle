package document.translation

import document.translation.delta.BlockChecksum
import document.translation.delta.BlockChecksumEntry
import document.translation.delta.BlockDelta
import document.translation.delta.BlockTranslationStatus
import contracts.i18n.TranslationRequest
import contracts.i18n.TranslationResult
import contracts.i18n.TranslationService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentTranslatorBlockDeltaTest {

    private val fakeService = CountingTranslationService()
    private val parser = AsciiDocParser()

    private fun parse(adoc: String): PivotArticle = parser.parse(adoc)

    private fun translated(hash: String) = BlockChecksumEntry(hash, BlockTranslationStatus.TRANSLATED)

    @Test
    fun `translateArticleWithDelta retranslates only modified blocks`() {
        val source = """title=Test
date=2026-08-04
type=page
status=published
~~~~~~

== Introduction

Premier paragraphe.

== Conclusion

Second paragraphe.
"""
        val sourceArticle = parse(source)
        val previousTranslated = PivotArticle(
            frontmatter = sourceArticle.frontmatter.copy(title = "Test [EN]"),
            blocks = listOf(
                PivotBlock.Heading(2, "Introduction [EN]", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Premier paragraphe. [EN]", translatable = true))),
                PivotBlock.Heading(2, "Conclusion [EN]", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Second paragraphe. [EN]", translatable = true))),
            )
        )
        val currentHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val previousEntries = currentHashes.mapValues { translated(it.value) }.toMutableMap()
        previousEntries["1"] = BlockChecksumEntry("different-hash", BlockTranslationStatus.TRANSLATED)
        val delta = BlockDelta.compute(previous = previousEntries, current = currentHashes)

        val translator = DocumentTranslator(fakeService)
        val result = translator.translateArticleWithDelta(
            sourceArticle, previousTranslated, delta, "fr", "en"
        )

        val heading0 = result.blocks[0] as PivotBlock.Heading
        assertEquals("Introduction [EN]", heading0.text)
        val para1 = result.blocks[1] as PivotBlock.Paragraph
        assertTrue(para1.inline[0].toString().contains("[EN]"))
        val heading2 = result.blocks[2] as PivotBlock.Heading
        assertEquals("Conclusion [EN]", heading2.text)
        val para3 = result.blocks[3] as PivotBlock.Paragraph
        assertTrue(para3.inline[0].toString().contains("[EN]"))
    }

    @Test
    fun `translateArticleWithDelta preserves diagram block when surrounding text changes`() {
        val sourceArticle = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-08-04", "page", "published"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro", translatable = true),
                PivotBlock.Source("plantuml", "@startuml\nA --> B\n@enduml"),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body text", translatable = true))),
            )
        )
        val previousTranslated = PivotArticle(
            frontmatter = sourceArticle.frontmatter.copy(title = "Test [EN]"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro [EN]", translatable = true),
                PivotBlock.Source("plantuml", "@startuml\nA --> B [EN]\n@enduml"),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body text [EN]", translatable = true))),
            )
        )
        val currentHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val previousEntries = currentHashes.mapValues { translated(it.value) }.toMutableMap()
        previousEntries["0"] = BlockChecksumEntry("heading-changed", BlockTranslationStatus.TRANSLATED)
        previousEntries["2"] = BlockChecksumEntry("para-changed", BlockTranslationStatus.TRANSLATED)
        val delta = BlockDelta.compute(previous = previousEntries, current = currentHashes)

        val translator = DocumentTranslator(fakeService)
        val result = translator.translateArticleWithDelta(
            sourceArticle, previousTranslated, delta, "fr", "en"
        )

        val heading = result.blocks[0] as PivotBlock.Heading
        assertTrue(heading.text.contains("[EN]"))
        val diagram = result.blocks[1] as PivotBlock.Source
        assertEquals("@startuml\nA --> B [EN]\n@enduml", diagram.content)
        val para = result.blocks[2] as PivotBlock.Paragraph
        assertTrue(para.inline[0].toString().contains("[EN]"))
    }

    @Test
    fun `translateArticleWithDelta with empty delta preserves all blocks from previous`() {
        val sourceArticle = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-08-04", "page", "published"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body", translatable = true))),
            )
        )
        val previousTranslated = PivotArticle(
            frontmatter = sourceArticle.frontmatter.copy(title = "Test [EN]"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro [EN]", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body [EN]", translatable = true))),
            )
        )
        val currentHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val previousEntries = currentHashes.mapValues { translated(it.value) }
        val delta = BlockDelta.compute(previous = previousEntries, current = currentHashes)

        val translator = DocumentTranslator(fakeService)
        val result = translator.translateArticleWithDelta(
            sourceArticle, previousTranslated, delta, "fr", "en"
        )

        val heading = result.blocks[0] as PivotBlock.Heading
        assertEquals("Intro [EN]", heading.text)
        val para = result.blocks[1] as PivotBlock.Paragraph
        assertTrue(para.inline[0].toString().contains("[EN]"))
    }

    @Test
    fun `translateArticleWithDelta with all modified retranslates everything`() {
        val sourceArticle = PivotArticle(
            frontmatter = PivotFrontmatter("Test", "2026-08-04", "page", "published"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body", translatable = true))),
            )
        )
        val previousTranslated = PivotArticle(
            frontmatter = sourceArticle.frontmatter.copy(title = "Test [OLD]"),
            blocks = listOf(
                PivotBlock.Heading(2, "Intro [OLD]", translatable = true),
                PivotBlock.Paragraph(listOf(PivotInline.Text("Body [OLD]", translatable = true))),
            )
        )
        val currentHashes = BlockChecksum.computeForBlocks(sourceArticle.blocks)
        val delta = BlockDelta.compute(previous = emptyMap(), current = currentHashes)

        val translator = DocumentTranslator(fakeService)
        val result = translator.translateArticleWithDelta(
            sourceArticle, previousTranslated, delta, "fr", "en"
        )

        val heading = result.blocks[0] as PivotBlock.Heading
        assertEquals("Intro [EN]", heading.text)
        val para = result.blocks[1] as PivotBlock.Paragraph
        assertTrue(para.inline[0].toString().contains("[EN]"))
    }

    private class CountingTranslationService : TranslationService {
        override fun translate(request: TranslationRequest): TranslationResult =
            TranslationResult.Success("${request.sourceText} [EN]")
    }
}