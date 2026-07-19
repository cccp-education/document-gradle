package document.translation

sealed class PivotBlock {
    abstract val translatable: Boolean

    data class Heading(
        val level: Int,
        val text: String,
        override val translatable: Boolean
    ) : PivotBlock()

    data class Paragraph(
        val inline: List<PivotInline>
    ) : PivotBlock() {
        override val translatable: Boolean
            get() = inline.any { it.translatable }
    }

    data class ListBlock(
        val ordered: Boolean,
        val items: List<List<PivotInline>>
    ) : PivotBlock() {
        override val translatable: Boolean
            get() = items.flatten().any { it.translatable }
    }

    data class Table(
        val cols: String?,
        val header: List<List<PivotInline>>,
        val rows: List<List<List<PivotInline>>>
    ) : PivotBlock() {
        override val translatable: Boolean
            get() = (header.flatten() + rows.flatten().flatten()).any { it.translatable }
    }

    data class Admonition(
        val kind: String,
        val blocks: List<PivotBlock>
    ) : PivotBlock() {
        override val translatable: Boolean
            get() = blocks.any { it.translatable }
    }

    data class Source(
        val language: String,
        val content: String
    ) : PivotBlock() {
        override val translatable: Boolean = false
    }

    data object Hr : PivotBlock() {
        override val translatable: Boolean = false
    }
}

sealed class PivotInline {
    abstract val translatable: Boolean

    data class Text(
        val text: String,
        override val translatable: Boolean
    ) : PivotInline()

    data class Bold(
        val text: String,
        override val translatable: Boolean
    ) : PivotInline()

    data class Code(
        val text: String,
        override val translatable: Boolean = false
    ) : PivotInline()

    data class Link(
        val url: String,
        val label: String,
        override val translatable: Boolean
    ) : PivotInline()
}

data class PivotFrontmatter(
    val title: String,
    val date: String,
    val type: String,
    val status: String,
    val author: String = "",
    val jbakeAttributes: Map<String, String> = emptyMap(),
    val asciidocAttributes: Map<String, String> = emptyMap()
) {
    val isJbakeNative: Boolean get() = jbakeAttributes.isNotEmpty() || author.isNotEmpty()
}

data class PivotArticle(
    val frontmatter: PivotFrontmatter,
    val blocks: List<PivotBlock>
)
