package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PivotModelTest {

    @Test
    fun `heading block carries level text and translatable flag`() {
        val block = PivotBlock.Heading(
            level = 2,
            text = "Guide de demarrage rapide",
            translatable = true
        )

        assertEquals(2, block.level)
        assertEquals("Guide de demarrage rapide", block.text)
        assertEquals(true, block.translatable)
    }

    @Test
    fun `paragraph block holds a list of inline segments`() {
        val block = PivotBlock.Paragraph(
            inline = listOf(
                PivotInline.Text("avec ", translatable = true),
                PivotInline.Bold("balenaEtcher", translatable = true),
                PivotInline.Text(" (recommande) :", translatable = true)
            )
        )

        assertEquals(3, block.inline.size)
        assertEquals("balenaEtcher", (block.inline[1] as PivotInline.Bold).text)
    }

    @Test
    fun `source block is non-translatable pass-through`() {
        val block = PivotBlock.Source(
            language = "bash",
            content = "sudo dd if=img.iso of=/dev/sdX bs=4M"
        )

        assertEquals("bash", block.language)
        assertEquals(false, block.translatable)
    }

    @Test
    fun `description list block holds items with term and definition`() {
        val block = PivotBlock.DescriptionList(
            items = listOf(
                DescriptionItem(
                    term = listOf(PivotInline.Link("https://a.com", "label", translatable = true)),
                    definition = listOf(PivotInline.Text("definition text", translatable = true))
                )
            )
        )

        assertEquals(1, block.items.size)
        assertEquals("label", (block.items[0].term[0] as PivotInline.Link).label)
        assertEquals("definition text", (block.items[0].definition[0] as PivotInline.Text).text)
        assertEquals(true, block.translatable)
    }

    @Test
    fun `description list with non-translatable items is non-translatable`() {
        val block = PivotBlock.DescriptionList(
            items = listOf(
                DescriptionItem(
                    term = listOf(PivotInline.Code("code", translatable = false)),
                    definition = listOf(PivotInline.Code("code", translatable = false))
                )
            )
        )

        assertEquals(false, block.translatable)
    }

    @Test
    fun `article aggregates frontmatter and ordered blocks`() {
        val article = PivotArticle(
            frontmatter = PivotFrontmatter(
                title = "Documentation",
                date = "2026-04-24",
                type = "page",
                status = "published"
            ),
            blocks = listOf(
                PivotBlock.Heading(level = 2, text = "Documentation", translatable = true)
            )
        )

        assertEquals("Documentation", article.frontmatter.title)
        assertEquals(1, article.blocks.size)
        assertEquals(PivotBlock.Heading(level = 2, text = "Documentation", translatable = true), article.blocks[0])
    }
}
