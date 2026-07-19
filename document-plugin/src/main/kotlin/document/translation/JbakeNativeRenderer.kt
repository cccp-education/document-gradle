package document.translation

class JbakeNativeRenderer : ArticleRenderer {

    override fun render(article: PivotArticle): String {
        val sb = StringBuilder()
        renderJbakeHeader(article.frontmatter, sb)
        article.blocks.forEachIndexed { i, block ->
            if (i > 0 || article.blocks.size > 1) sb.appendLine()
            renderBlock(block, sb)
        }
        return sb.toString().trimEnd()
    }

    private fun renderJbakeHeader(fm: PivotFrontmatter, sb: StringBuilder) {
        sb.appendLine("= ${fm.title}")
        if (fm.author.isNotEmpty()) {
            sb.appendLine("@${fm.author}")
        }
        if (fm.date.isNotEmpty()) {
            sb.appendLine(fm.date)
        }
        val attrs = fm.jbakeAttributes.toMutableMap()
        if (fm.title.isNotEmpty()) attrs["title"] = fm.title
        if (fm.type.isNotEmpty()) attrs["type"] = fm.type
        if (fm.status.isNotEmpty()) attrs["status"] = fm.status
        if (fm.date.isNotEmpty()) attrs["date"] = fm.date

        val orderedKeys = listOf("title", "tags", "type", "status", "date", "summary")
        val rendered = mutableSetOf<String>()
        for (key in orderedKeys) {
            attrs[key]?.let { value ->
                sb.appendLine(":jbake-$key: $value")
                rendered.add(key)
            }
        }
        for ((key, value) in attrs) {
            if (key !in rendered) {
                sb.appendLine(":jbake-$key: $value")
            }
        }
        for ((key, value) in fm.asciidocAttributes) {
            sb.appendLine(":$key: $value")
        }
        sb.appendLine()
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
        val line = p.inline.joinToString("") { renderInline(it) }
        sb.appendLine(line)
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
        sb.appendLine("====")
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
    }
}
