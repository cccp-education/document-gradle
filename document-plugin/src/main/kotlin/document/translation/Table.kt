package document.translation

data class Table(
    val cols: List<ColSpec>,
    val header: List<Row>,
    val body: List<Row>,
) {
    val allRows: List<Row> get() = header + body

    fun extractTranslatable(): List<TranslatableSegment> {
        val segments = mutableListOf<TranslatableSegment>()
        allRows.forEachIndexed { rowIdx, row ->
            row.cells.forEachIndexed { cellIdx, cell ->
                cell.inline.forEachIndexed { inlineIdx, node ->
                    if (node.translatable && node is PivotInline.Text) {
                        segments.add(
                            TranslatableSegment(
                                rowIndex = rowIdx,
                                cellIndex = cellIdx,
                                inlineIndex = inlineIdx,
                                text = node.text,
                            ),
                        )
                    }
                }
            }
        }
        return segments
    }

    fun reinject(translations: Map<Int, String>): Table {
        val newRows = allRows.mapIndexed { rowIdx, row ->
            val newCells = row.cells.mapIndexed { cellIdx, cell ->
                val newInline = cell.inline.mapIndexed { inlineIdx, node ->
                    val key = segmentKey(rowIdx, cellIdx, inlineIdx)
                    if (key in translations && node is PivotInline.Text && node.translatable) {
                        node.copy(text = translations[key]!!)
                    } else {
                        node
                    }
                }
                cell.copy(inline = newInline)
            }
            row.copy(cells = newCells)
        }
        val headerSize = header.size
        return copy(
            header = newRows.take(headerSize),
            body = newRows.drop(headerSize),
        )
    }

    fun toAsciiDoc(): String {
        val sb = StringBuilder()
        if (cols.isNotEmpty()) {
            val colSpec = cols.joinToString(",") { it.toAsciiDoc() }
            sb.append("[cols=\"$colSpec\"]\n")
        }
        sb.append("|===\n")
        allRows.forEachIndexed { idx, row ->
            row.cells.forEach { cell ->
                sb.append(cell.toAsciiDoc())
            }
            sb.append("\n")
            if (header.isNotEmpty() && idx == header.size - 1) {
                sb.append("\n")
            }
        }
        sb.append("|===\n")
        return sb.toString()
    }

    fun toHtml(): String {
        val sb = StringBuilder()
        sb.append("<table>\n")
        if (header.isNotEmpty()) {
            sb.append("  <thead>\n")
            header.forEach { row ->
                sb.append("    <tr>\n")
                row.cells.forEach { cell ->
                    sb.append("      <th${cell.htmlAttrs()}>${cell.toHtmlContent()}</th>\n")
                }
                sb.append("    </tr>\n")
            }
            sb.append("  </thead>\n")
        }
        if (body.isNotEmpty()) {
            sb.append("  <tbody>\n")
            body.forEach { row ->
                sb.append("    <tr>\n")
                row.cells.forEach { cell ->
                    sb.append("      <td${cell.htmlAttrs()}>${cell.toHtmlContent()}</td>\n")
                }
                sb.append("    </tr>\n")
            }
            sb.append("  </tbody>\n")
        }
        sb.append("</table>\n")
        return sb.toString()
    }

    private fun segmentKey(rowIdx: Int, cellIdx: Int, inlineIdx: Int): Int =
        (rowIdx * 10000) + (cellIdx * 100) + inlineIdx
}

data class ColSpec(
    val width: Int? = null,
    val halign: HAlign? = null,
    val valign: VAlign? = null,
) {
    fun toAsciiDoc(): String {
        val parts = mutableListOf<String>()
        if (valign != null) parts.add(valign.asciiDoc)
        if (halign != null) parts.add(halign.asciiDoc)
        if (width != null) parts.add(width.toString())
        return parts.joinToString("")
    }
}

enum class HAlign(val asciiDoc: String) {
    LEFT("<"),
    CENTER("^"),
    RIGHT(">"),
}

enum class VAlign(val asciiDoc: String) {
    TOP(".<"),
    MIDDLE(".^"),
    BOTTOM(".>"),
}

data class Row(
    val cells: List<Cell>,
)

data class Cell(
    val inline: List<PivotInline>,
    val colSpan: Int = 1,
    val rowSpan: Int = 1,
    val halign: HAlign? = null,
    val valign: VAlign? = null,
) {
    fun toAsciiDoc(): String {
        val prefix = buildString {
            if (colSpan > 1) append("$colSpan+")
            if (rowSpan > 1) append(".$rowSpan+")
            if (halign != null) append(halign.asciiDoc)
            if (valign != null) append(valign.asciiDoc)
        }
        val content = inline.joinToString("") { it.toAsciiDoc() }
        return "|$prefix$content "
    }

    fun toHtmlContent(): String =
        inline.joinToString("") { it.toHtml() }

    fun htmlAttrs(): String {
        val attrs = mutableListOf<String>()
        if (colSpan > 1) attrs.add("colspan=\"$colSpan\"")
        if (rowSpan > 1) attrs.add("rowspan=\"$rowSpan\"")
        val align = halign?.html ?: valign?.html
        if (align != null) attrs.add("style=\"text-align: $align\"")
        return if (attrs.isEmpty()) "" else " " + attrs.joinToString(" ")
    }
}

data class TranslatableSegment(
    val rowIndex: Int,
    val cellIndex: Int,
    val inlineIndex: Int,
    val text: String,
)

private fun PivotInline.toAsciiDoc(): String = when (this) {
    is PivotInline.Text -> text
    is PivotInline.Bold -> "**$text**"
    is PivotInline.Code -> "`$text`"
    is PivotInline.Link -> "link:$url[$label]"
    is PivotInline.LineBreak -> " +\n"
}

private fun PivotInline.toHtml(): String = when (this) {
    is PivotInline.Text -> text
    is PivotInline.Bold -> "<strong>$text</strong>"
    is PivotInline.Code -> "<code>$text</code>"
    is PivotInline.Link -> "<a href=\"$url\">$label</a>"
    is PivotInline.LineBreak -> "<br>"
}

private val HAlign.html: String? get() = when (this) {
    HAlign.LEFT -> "left"
    HAlign.CENTER -> "center"
    HAlign.RIGHT -> "right"
}

private val VAlign.html: String? get() = when (this) {
    VAlign.TOP -> "top"
    VAlign.MIDDLE -> "middle"
    VAlign.BOTTOM -> "bottom"
}
