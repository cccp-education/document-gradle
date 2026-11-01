package document.translation.tree

import document.translation.PivotArticle
import document.translation.PivotBlock
import document.translation.PivotInline
import document.translation.TextTranslatableClassifier
import document.translation.fold

data class Content(
    val pivot: PivotArticle,
) {
    fun blocs(): List<PivotBlock> = pivot.blocks

    fun inlineTexts(): List<PivotInline> = pivot.blocks.flatMap { inlineOf(it) }

    private fun inlineOf(block: PivotBlock): List<PivotInline> =
        block.fold(
            heading = { emptyList() },
            paragraph = { it.inline },
            listBlock = { it.items.flatten() },
            table = { it.header.flatten() + it.rows.flatten().flatten() },
            admonition = { it.blocks.flatMap { inner -> inlineOf(inner) } },
            source = { emptyList() },
            descriptionList = { it.items.flatMap { item -> item.term + item.definition } },
            blockMacro = { emptyList() },
            hr = { emptyList() },
        )

    fun translatableSegments(): List<PivotInline> = inlineTexts().filter { it.translatable && TextTranslatableClassifier.isTranslatable(it.textValue()) }
}

private fun PivotInline.textValue(): String =
    when (this) {
        is PivotInline.Text -> text
        is PivotInline.Bold -> text
        is PivotInline.Code -> text
        is PivotInline.Link -> label
        is PivotInline.LineBreak -> "\n"
    }
