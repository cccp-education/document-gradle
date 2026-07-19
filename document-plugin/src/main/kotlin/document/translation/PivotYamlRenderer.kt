package document.translation

class PivotYamlRenderer {

    fun render(article: PivotArticle): String {
        val sb = StringBuilder()
        sb.append("article:\n")
        renderFrontmatter(article.frontmatter, sb, INDENT_1)
        sb.append("\n  blocks:\n")
        article.blocks.forEachIndexed { index, block ->
            renderBlock(block, sb, INDENT_2, isLast = index == article.blocks.size - 1)
        }
        return sb.toString()
    }

    private fun renderFrontmatter(fm: PivotFrontmatter, sb: StringBuilder, indent: String) {
        sb.append("${indent}frontmatter:\n")
        sb.append("${indent}${INDENT_1}title: ${yamlString(fm.title)}\n")
        sb.append("${indent}${INDENT_1}date: ${yamlString(fm.date)}\n")
        sb.append("${indent}${INDENT_1}type: ${yamlString(fm.type)}\n")
        sb.append("${indent}${INDENT_1}status: ${yamlString(fm.status)}\n")
    }

    private fun renderBlock(block: PivotBlock, sb: StringBuilder, indent: String, isLast: Boolean) {
        sb.append("${indent}- ")
        when (block) {
            is PivotBlock.Heading -> renderHeading(block, sb, indent)
            is PivotBlock.Paragraph -> renderParagraph(block, sb, indent)
            is PivotBlock.ListBlock -> renderList(block, sb, indent)
            is PivotBlock.Table -> renderTable(block, sb, indent)
            is PivotBlock.Admonition -> renderAdmonition(block, sb, indent)
            is PivotBlock.Source -> renderSource(block, sb, indent)
            PivotBlock.Hr -> renderHr(sb, indent)
        }
    }

    private fun renderHeading(h: PivotBlock.Heading, sb: StringBuilder, indent: String) {
        sb.append("type: heading\n")
        sb.append("$indent${INDENT_1}level: ${h.level}\n")
        sb.append("$indent${INDENT_1}text: ${yamlString(h.text)}\n")
        sb.append("$indent${INDENT_1}translatable: ${h.translatable}\n")
    }

    private fun renderParagraph(p: PivotBlock.Paragraph, sb: StringBuilder, indent: String) {
        sb.append("type: paragraph\n")
        sb.append("$indent${INDENT_1}inline:\n")
        p.inline.forEach { renderInline(it, sb, "$indent${INDENT_1}${INDENT_1}") }
    }

    private fun renderList(list: PivotBlock.ListBlock, sb: StringBuilder, indent: String) {
        sb.append("type: list\n")
        sb.append("$indent${INDENT_1}ordered: ${list.ordered}\n")
        sb.append("$indent${INDENT_1}items:\n")
        list.items.forEach { item ->
            sb.append("$indent${INDENT_1}${INDENT_1}- inline:\n")
            item.forEach { renderInline(it, sb, "$indent${INDENT_1}${INDENT_1}${INDENT_1}${INDENT_1}") }
        }
    }

    private fun renderTable(table: PivotBlock.Table, sb: StringBuilder, indent: String) {
        sb.append("type: table\n")
        sb.append("$indent${INDENT_1}cols: ${colsSpec(table.cols)}\n")
        sb.append("$indent${INDENT_1}header:\n")
        table.header.forEach { cell ->
            sb.append("$indent${INDENT_1}${INDENT_1}- inline:\n")
            cell.forEach { renderInline(it, sb, "$indent${INDENT_1}${INDENT_1}${INDENT_1}${INDENT_1}") }
        }
        sb.append("$indent${INDENT_1}rows:\n")
        table.rows.forEach { row ->
            sb.append("$indent${INDENT_1}${INDENT_1}- ")
            row.forEachIndexed { index, cell ->
                if (index > 0) sb.append("$indent${INDENT_1}${INDENT_1}  ")
                sb.append("- inline:\n")
                cell.forEach { renderInline(it, sb, "$indent${INDENT_1}${INDENT_1}${INDENT_1}${INDENT_1}${INDENT_1}") }
            }
        }
    }

    private fun renderAdmonition(adm: PivotBlock.Admonition, sb: StringBuilder, indent: String) {
        sb.append("type: admonition\n")
        sb.append("$indent${INDENT_1}kind: ${adm.kind}\n")
        sb.append("$indent${INDENT_1}blocks:\n")
        adm.blocks.forEach { renderBlock(it, sb, "$indent${INDENT_1}${INDENT_1}", isLast = false) }
    }

    private fun renderSource(src: PivotBlock.Source, sb: StringBuilder, indent: String) {
        sb.append("type: source\n")
        sb.append("$indent${INDENT_1}language: ${yamlScalar(src.language)}\n")
        sb.append("$indent${INDENT_1}translatable: false\n")
        sb.append("$indent${INDENT_1}content: |\n")
        src.content.lines().forEach { line ->
            sb.append("$indent${INDENT_1}${INDENT_1}$line\n")
        }
    }

    private fun renderHr(sb: StringBuilder, indent: String) {
        sb.append("type: hr\n")
        sb.append("$indent${INDENT_1}translatable: false\n")
    }

    private fun renderInline(inline: PivotInline, sb: StringBuilder, indent: String) {
        when (inline) {
            is PivotInline.Text -> {
                sb.append("$indent- text: ${yamlString(inline.text)}\n")
                sb.append("$indent${INDENT_1}translatable: ${inline.translatable}\n")
            }
            is PivotInline.Bold -> {
                sb.append("$indent- type: bold\n")
                sb.append("$indent${INDENT_1}text: ${yamlString(inline.text)}\n")
                sb.append("$indent${INDENT_1}translatable: ${inline.translatable}\n")
            }
            is PivotInline.Code -> {
                sb.append("$indent- type: code\n")
                sb.append("$indent${INDENT_1}text: ${yamlString(inline.text)}\n")
                sb.append("$indent${INDENT_1}translatable: ${inline.translatable}\n")
            }
            is PivotInline.Link -> {
                sb.append("$indent- type: link\n")
                sb.append("$indent${INDENT_1}url: ${yamlString(inline.url)}\n")
                sb.append("$indent${INDENT_1}label: ${yamlString(inline.label)}\n")
                sb.append("$indent${INDENT_1}translatable: ${inline.translatable}\n")
            }
        }
    }

    private fun colsSpec(cols: String?): String =
        if (cols == null) "null" else "\"$cols\""

    private fun yamlString(s: String): String =
        if (s.isEmpty()) "\"\"" else "\"${s.replace("\\", "\\\\").replace("\"", "\\\"")}\""

    private fun yamlScalar(s: String): String =
        if (s.isEmpty()) "\"\"" else s

    companion object {
        private const val INDENT_1 = "  "
        private const val INDENT_2 = "    "
    }
}
