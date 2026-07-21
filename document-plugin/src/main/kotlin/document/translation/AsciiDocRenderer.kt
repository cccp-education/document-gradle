package document.translation

interface ArticleRenderer {
    fun render(article: PivotArticle): String
}

class AsciiDocRenderer : ArticleRenderer {

    override fun render(article: PivotArticle): String {
        val sb = StringBuilder()
        renderFrontmatter(article.frontmatter, sb)
        article.blocks.forEachIndexed { i, block ->
            if (i > 0 || article.blocks.size > 1) sb.appendLine()
            renderBlock(block, sb)
        }
        return sb.toString()
    }

    private fun renderFrontmatter(fm: PivotFrontmatter, sb: StringBuilder) {
        sb.appendLine("title=${fm.title}")
        sb.appendLine("date=${fm.date}")
        sb.appendLine("type=${fm.type}")
        sb.appendLine("status=${fm.status}")
        if (fm.author.isNotEmpty()) sb.appendLine("author=${fm.author}")
        fm.jbakeAttributes.forEach { (k, v) -> sb.appendLine("jbake-$k=$v") }
        fm.asciidocAttributes.forEach { (k, v) -> sb.appendLine("$k=$v") }
        sb.appendLine("~~~~~~")
    }

    private fun renderBlock(block: PivotBlock, sb: StringBuilder) {
        when (block) {
            is PivotBlock.Heading -> renderHeading(block, sb)
            is PivotBlock.Paragraph -> renderParagraph(block, sb)
            is PivotBlock.ListBlock -> renderList(block, sb)
            is PivotBlock.Table -> renderTable(block, sb)
            is PivotBlock.Admonition -> renderAdmonition(block, sb)
            is PivotBlock.Source -> renderSource(block, sb)
            is PivotBlock.Hr -> sb.appendLine("---")
        }
    }

    private fun renderHeading(h: PivotBlock.Heading, sb: StringBuilder) {
        val prefix = "=".repeat(h.level)
        sb.appendLine("$prefix ${h.text}")
    }

    private fun renderParagraph(p: PivotBlock.Paragraph, sb: StringBuilder) {
        val lines = splitParagraphLines(p.inline)
        for ((idx, line) in lines.withIndex()) {
            if (idx < lines.size - 1) {
                sb.appendLine("$line +")
            } else {
                sb.appendLine(line)
            }
        }
    }

    private fun splitParagraphLines(inlines: List<PivotInline>): List<String> {
        val lines = mutableListOf<String>()
        val current = StringBuilder()
        for (inline in inlines) {
            when (inline) {
                is PivotInline.LineBreak -> {
                    lines.add(current.toString().trimEnd())
                    current.clear()
                }
                else -> current.append(renderInline(inline))
            }
        }
        if (current.isNotEmpty() || lines.isEmpty()) {
            lines.add(current.toString().trimEnd())
        }
        return lines
    }

    private fun renderList(list: PivotBlock.ListBlock, sb: StringBuilder) {
        for ((idx, item) in list.items.withIndex()) {
            val prefix = if (list.ordered) "${idx + 1}. " else "* "
            val line = item.joinToString("") { renderInline(it) }
            sb.appendLine("$prefix$line")
        }
    }

    private fun renderTable(table: PivotBlock.Table, sb: StringBuilder) {
        if (table.cols != null) {
            sb.appendLine("[cols=\"${table.cols}\"]")
        }
        sb.appendLine("|===")
        if (table.header.isNotEmpty()) {
            sb.appendLine(table.header.joinToString(" ") { cells ->
                cells.joinToString("") { renderInline(it) }.let { "|$it" }
            })
        }
        if (table.rows.isNotEmpty()) {
            sb.appendLine()
        }
        for (row in table.rows) {
            sb.appendLine(row.joinToString(" ") { cells ->
                cells.joinToString("") { renderInline(it) }.let { "|$it" }
            })
        }
        sb.appendLine("|===")
    }

    private fun renderAdmonition(adm: PivotBlock.Admonition, sb: StringBuilder) {
        sb.appendLine("[${adm.kind}]")
        sb.appendLine("====")
        for (block in adm.blocks) {
            renderBlock(block, sb)
        }
        if (adm.blocks.none { it is PivotBlock.Hr }) {
            sb.appendLine("====")
        } else {
            sb.appendLine("====")
        }
    }

    private fun renderSource(src: PivotBlock.Source, sb: StringBuilder) {
        val lang = if (src.language.isNotEmpty()) ",${src.language}" else ""
        sb.appendLine("[source$lang]")
        sb.appendLine("----")
        sb.appendLine(src.content)
        sb.appendLine("----")
    }

    private fun renderInline(inline: PivotInline): String = when (inline) {
        is PivotInline.Text -> inline.text
        is PivotInline.Bold -> "**${inline.text}**"
        is PivotInline.Code -> "`${inline.text}`"
        is PivotInline.Link -> {
            if (inline.url.startsWith("http://") || inline.url.startsWith("https://")) {
                "${inline.url}[${inline.label}]"
            } else {
                "link:${inline.url}[${inline.label}]"
            }
        }
        is PivotInline.LineBreak -> ""
    }
}
