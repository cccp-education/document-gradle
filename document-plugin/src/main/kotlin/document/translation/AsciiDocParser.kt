package document.translation

class AsciiDocParser {

    fun parse(adoc: String): PivotArticle {
        val lines = adoc.lines()
        return if (isPivotFormat(lines)) {
            parsePivotFormat(lines)
        } else {
            parseJbakeNativeFormat(lines)
        }
    }

    private fun isPivotFormat(lines: List<String>): Boolean =
        lines.any { it.startsWith("~~~~~~") }

    private fun parsePivotFormat(lines: List<String>): PivotArticle {
        val frontmatter = parseFrontmatter(lines)
        val bodyStart = indexOfTildeSeparator(lines) + 1
        val bodyLines = lines.drop(bodyStart)
        val blocks = parseBlocks(bodyLines)
        return PivotArticle(frontmatter, blocks)
    }

    private fun parseJbakeNativeFormat(lines: List<String>): PivotArticle {
        val (frontmatter, bodyStart) = parseJbakeHeader(lines)
        val bodyLines = lines.drop(bodyStart)
        val blocks = parseBlocks(bodyLines)
        return PivotArticle(frontmatter, blocks)
    }

    private fun parseJbakeHeader(lines: List<String>): Pair<PivotFrontmatter, Int> {
        var title = ""
        var date = ""
        var type = ""
        var status = ""
        var author = ""
        val jbakeAttributes = mutableMapOf<String, String>()
        val asciidocAttributes = mutableMapOf<String, String>()
        var i = 0

        if (i < lines.size && lines[i].startsWith("= ")) {
            title = lines[i].removePrefix("= ").trim()
            i++
        }

        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) break
            when {
                line.startsWith("@") -> {
                    author = line.removePrefix("@").trim()
                    i++
                }
                line.matches(Regex("^\\d{4}-\\d{2}-\\d{2}.*")) -> {
                    date = line.trim()
                    i++
                }
                line.startsWith(":jbake-title:") -> {
                    title = line.removePrefix(":jbake-title:").trim().ifEmpty { title }
                    jbakeAttributes["title"] = title
                    i++
                }
                line.startsWith(":jbake-date:") -> {
                    date = line.removePrefix(":jbake-date:").trim().ifEmpty { date }
                    jbakeAttributes["date"] = date
                    i++
                }
                line.startsWith(":jbake-type:") -> {
                    type = line.removePrefix(":jbake-type:").trim()
                    jbakeAttributes["type"] = type
                    i++
                }
                line.startsWith(":jbake-status:") -> {
                    status = line.removePrefix(":jbake-status:").trim()
                    jbakeAttributes["status"] = status
                    i++
                }
                line.startsWith(":jbake-") -> {
                    val key = line.removePrefix(":jbake-").substringBefore(":").trim()
                    val value = line.substringAfter(":jbake-$key:").trim()
                    jbakeAttributes[key] = value
                    i++
                }
                line.startsWith(":") -> {
                    val key = line.removePrefix(":").substringBefore(":").trim()
                    val value = line.substringAfter(":$key:").trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) {
                        asciidocAttributes[key] = value
                    }
                    i++
                }
                else -> i++
            }
        }

        return PivotFrontmatter(
            title = title,
            date = date,
            type = type,
            status = status,
            author = author,
            jbakeAttributes = jbakeAttributes,
            asciidocAttributes = asciidocAttributes
        ) to i
    }

    private fun parseFrontmatter(lines: List<String>): PivotFrontmatter {
        var title = ""
        var date = ""
        var type = ""
        var status = ""
        var author = ""
        val jbakeAttributes = mutableMapOf<String, String>()
        for (line in lines) {
            if (line.startsWith("title=")) title = line.removePrefix("title=").trim()
            else if (line.startsWith("date=")) date = line.removePrefix("date=").trim()
            else if (line.startsWith("type=")) type = line.removePrefix("type=").trim()
            else if (line.startsWith("status=")) status = line.removePrefix("status=").trim()
            else if (line.startsWith("author=")) author = line.removePrefix("author=").trim()
            else if (line.startsWith("jbake-")) {
                val key = line.removePrefix("jbake-").substringBefore("=").trim()
                val value = line.substringAfter("=").trim()
                jbakeAttributes[key] = value
            }
            else if (line.startsWith("~~~~~~")) break
        }
        return PivotFrontmatter(title, date, type, status, author, jbakeAttributes)
    }

    private fun indexOfTildeSeparator(lines: List<String>): Int =
        lines.indexOfFirst { it.startsWith("~~~~~~") }.let { if (it < 0) 0 else it }

    private fun parseBlocks(lines: List<String>): List<PivotBlock> {
        val blocks = mutableListOf<PivotBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) { i++; continue }

            when {
                line.trim() == "'''" -> {
                    i++
                }
                line.trim().startsWith("//") -> {
                    i++
                }
                line.startsWith("====") && isAdmonitionDelimiter(line) -> {
                    i++
                }
                line.startsWith("---") && isHr(line) -> {
                    blocks.add(PivotBlock.Hr)
                    i++
                }
                line.startsWith("=") -> {
                    val heading = parseHeading(line)
                    blocks.add(heading)
                    i++
                }
                line.startsWith("[source") || line.startsWith("[plantuml") -> {
                    val (block, next) = parseSourceBlock(lines, i)
                    blocks.add(block)
                    i = next
                }
                isMarkdownFence(line) -> {
                    val (block, next) = parseMarkdownFencedBlock(lines, i)
                    blocks.add(block)
                    i = next
                }
                line.startsWith("[") && line.endsWith("]") && !line.startsWith("[cols") -> {
                    val kind = line.removeSurrounding("[", "]").trim()
                    if (kind in ADMONITION_KINDS) {
                        val (adm, next) = parseAdmonition(lines, i, kind)
                        blocks.add(adm)
                        i = next
                    } else {
                        i++
                    }
                }
                line.startsWith("[cols=") -> {
                    val (table, next) = parseTable(lines, i, cols = extractCols(line))
                    blocks.add(table)
                    i = next
                }
                line.startsWith("|===") -> {
                    val (table, next) = parseTable(lines, i, cols = null)
                    blocks.add(table)
                    i = next
                }
                isUnorderedListMarker(line) || line.startsWith(". ") || isNumberedListMarker(line) -> {
                    val (list, next) = parseList(lines, i)
                    blocks.add(list)
                    i = next
                }
                isDescriptionListItem(line) -> {
                    val (dl, next) = parseDescriptionList(lines, i)
                    blocks.add(dl)
                    i = next
                }
                isBlockMacro(line) -> {
                    blocks.add(parseBlockMacro(line))
                    i++
                }
                else -> {
                    val (para, next) = parseParagraph(lines, i)
                    if (para != null) blocks.add(para)
                    i = next
                }
            }
        }
        return blocks
    }

    private fun isHr(line: String): Boolean = line == "---"

    private fun isAdmonitionDelimiter(line: String): Boolean =
        line.length >= 4 && line.all { it == '=' }

    private fun parseHeading(line: String): PivotBlock.Heading {
        val match = Regex("^(=+)\\s+(.+)$").find(line)!!
        val level = match.groupValues[1].length
        val text = match.groupValues[2].trim()
        return PivotBlock.Heading(level, text, translatable = true)
    }

    private fun parseSourceBlock(lines: List<String>, start: Int): Pair<PivotBlock.Source, Int> {
        val header = lines[start]
        val language = when {
            header.startsWith("[source") -> Regex("\\[source,?\\s*(\\w*)\\]").find(header)?.groupValues?.get(1)?.trim() ?: ""
            header.startsWith("[plantuml") -> "plantuml"
            else -> ""
        }
        var i = start + 1
        val delimiter = firstBlockDelimiterAfterHeader(lines, i)
        if (delimiter != null) {
            i = delimiter.second
            val contentStart = i
            while (i < lines.size && !isSourceClosingDelimiter(lines[i])) i++
            val content = lines.subList(contentStart, i).joinToString("\n")
            if (i < lines.size) i++
            return PivotBlock.Source(language, content, header) to i
        }
        return PivotBlock.Source(language, "", header) to lines.size
    }

    private fun firstBlockDelimiterAfterHeader(lines: List<String>, from: Int): Pair<String, Int>? {
        var i = from
        while (i < lines.size) {
            val line = lines[i].trim()
            if (isSourceDelimiterLine(line)) return line to i + 1
            if (isBlockTitleLine(line)) { i++; continue }
            if (line.isNotBlank()) return null
            i++
        }
        return null
    }

    private fun isBlockTitleLine(line: String): Boolean =
        line.startsWith(".") && !line.startsWith("..") && line.length > 1

    private fun isSourceClosingDelimiter(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        return isSourceDelimiterLine(trimmed)
    }

    private fun isSourceDelimiterLine(line: String): Boolean {
        if (line.length < 4) return false
        return line.all { it == '-' } || line.all { it == '=' } || line.all { it == '.' }
    }

    private fun isMarkdownFence(line: String): Boolean = line.trim().startsWith("```")

    private fun parseMarkdownFencedBlock(lines: List<String>, start: Int): Pair<PivotBlock.Source, Int> {
        val fenceLine = lines[start].trim()
        val language = fenceLine.removePrefix("```").trim()
        var i = start + 1
        val contentStart = i
        while (i < lines.size && !lines[i].trim().startsWith("```")) i++
        val content = lines.subList(contentStart, i).joinToString("\n")
        if (i < lines.size) i++
        return PivotBlock.Source(language, content, lines[start]) to i
    }

    private fun parseAdmonition(lines: List<String>, start: Int, kind: String): Pair<PivotBlock.Admonition, Int> {
        if (start + 1 >= lines.size) {
            return PivotBlock.Admonition(kind, emptyList()) to start + 1
        }
        if (!lines[start + 1].startsWith("====")) {
            val (para, next) = parseParagraph(lines, start + 1)
            val blocks = if (para != null) listOf(para) else emptyList()
            return PivotBlock.Admonition(kind, blocks) to next
        }
        var i = start + 1
        while (i < lines.size && !lines[i].startsWith("====")) i++
        i++
        val contentStart = i
        while (i < lines.size && !lines[i].startsWith("====")) i++
        val contentLines = lines.subList(contentStart, i).filter { it.isNotBlank() }
        if (i < lines.size) i++
        val blocks = parseBlocks(contentLines)
        return PivotBlock.Admonition(kind, blocks) to i
    }

    private fun extractCols(line: String): String? {
        val match = Regex("\\[cols=\"([^\"]+)\"\\]").find(line)
        return match?.groupValues?.get(1)
    }

    fun parseTableStructured(lines: List<String>, start: Int, cols: String?): Pair<Table, Int> {
        val colSpecs = parseColSpecs(cols)
        var i = start
        if (lines[i].startsWith("[cols=")) i++
        if (i < lines.size && lines[i].startsWith("|===")) i++
        val cellLines = mutableListOf<String>()
        while (i < lines.size && !lines[i].startsWith("|===")) {
            cellLines.add(lines[i])
            i++
        }
        if (i < lines.size) i++

        val rows = parseStructuredRows(cellLines)
        val header = if (rows.isNotEmpty()) listOf(rows.first()) else emptyList()
        val body = if (rows.size > 1) rows.drop(1) else emptyList()
        return Table(colSpecs, header, body) to i
    }

    internal fun parseColSpecs(raw: String?): List<ColSpec> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(",").map { spec -> parseSingleColSpec(spec.trim()) }
    }

    private fun parseSingleColSpec(spec: String): ColSpec {
        var remaining = spec
        var halign: HAlign? = null
        var valign: VAlign? = null
        var width: Int? = null

        if (remaining.startsWith(".<")) { valign = VAlign.TOP; remaining = remaining.removePrefix(".<") }
        else if (remaining.startsWith(".^")) { valign = VAlign.MIDDLE; remaining = remaining.removePrefix(".^") }
        else if (remaining.startsWith(".>")) { valign = VAlign.BOTTOM; remaining = remaining.removePrefix(".>") }

        if (remaining.startsWith("<")) { halign = HAlign.LEFT; remaining = remaining.removePrefix("<") }
        else if (remaining.startsWith("^")) { halign = HAlign.CENTER; remaining = remaining.removePrefix("^") }
        else if (remaining.startsWith(">")) { halign = HAlign.RIGHT; remaining = remaining.removePrefix(">") }

        if (remaining.isNotEmpty()) {
            val digits = remaining.takeWhile { it.isDigit() }
            if (digits.isNotEmpty()) {
                width = digits.toIntOrNull()
            }
        }
        return ColSpec(width, halign, valign)
    }

    private fun parseStructuredRows(cellLines: List<String>): List<Row> {
        val rows = mutableListOf<Row>()
        var currentCells = mutableListOf<Cell>()
        for (line in cellLines) {
            if (line.isBlank()) {
                if (currentCells.isNotEmpty()) {
                    rows.add(Row(currentCells.toList()))
                    currentCells = mutableListOf()
                }
                continue
            }
            if (!line.startsWith("|") && !isCellPrefixLine(line)) {
                if (currentCells.isNotEmpty()) {
                    val lastCell = currentCells.removeAt(currentCells.size - 1)
                    val lastText = (lastCell.inline.lastOrNull() as? PivotInline.Text)?.text.orEmpty()
                    val rest = lastCell.inline.dropLast(1)
                    val mergedText = if (lastText.isEmpty()) line.trim() else "$lastText ${line.trim()}"
                    val newInline = rest + listOf(
                        PivotInline.Text(mergedText, translatable = TextTranslatableClassifier.isTranslatable(mergedText)),
                    )
                    currentCells.add(lastCell.copy(inline = newInline))
                }
                continue
            }
            val cells = splitStructuredCells(line)
            for (cellContent in cells) {
                val prefix = parseCellPrefix(cellContent)
                val inline = parseInline(prefix.content.trim())
                currentCells.add(
                    Cell(
                        inline = inline,
                        colSpan = prefix.colSpan,
                        rowSpan = prefix.rowSpan,
                        halign = prefix.halign,
                        valign = prefix.valign,
                    ),
                )
            }
        }
        if (currentCells.isNotEmpty()) rows.add(Row(currentCells.toList()))
        return rows
    }

    private fun isCellPrefixLine(line: String): Boolean {
        val trimmed = line.trimStart()
        return CELL_PREFIX_LINE_PATTERN.containsMatchIn(trimmed)
    }

    private val CELL_PREFIX_LINE_PATTERN = Regex("^(\\d+)?\\+?(\\.(\\d+)\\+)?[<^>]?(\\.[<^>])?[ah]?\\|")

    private fun splitStructuredCells(line: String): List<String> {
        if (!line.startsWith("|")) return listOf(line)
        val result = mutableListOf<String>()
        var current = StringBuilder()
        val content = line.removePrefix("|")
        var inCode = false
        var i = 0
        while (i < content.length) {
            val ch = content[i]
            if (ch == '\\' && i + 1 < content.length && content[i + 1] == '|') {
                current.append('|')
                i += 2
                continue
            }
            if (ch == '`') inCode = !inCode
            if (ch == '|' && !inCode && !isCellPrefix(current.toString())) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(ch)
            }
            i++
        }
        result.add(current.toString())
        return result
    }

    private fun isCellPrefix(s: String): Boolean {
        val trimmed = s.trimStart()
        if (trimmed.isEmpty()) return false
        return CELL_PREFIX_PATTERN.matches(trimmed)
    }

    private val CELL_PREFIX_PATTERN = Regex("^(\\d+)?\\+?(\\.(\\d+)\\+)?[<^>]?(\\.[<^>])?[ah]?$")

    private fun parseCellPrefix(raw: String): CellPrefix {
        var remaining = raw.trimStart()
        var colSpan = 1
        var rowSpan = 1
        var halign: HAlign? = null
        var valign: VAlign? = null

        val spanMatch = Regex("^(\\d+)?\\+?(\\.(\\d+)\\+)?").find(remaining)
        if (spanMatch != null && spanMatch.value.isNotEmpty()) {
            val colStr = spanMatch.groupValues[1]
            val rowStr = spanMatch.groupValues[3]
            if (colStr.isNotEmpty()) colSpan = colStr.toInt()
            if (rowStr.isNotEmpty()) rowSpan = rowStr.toInt()
            remaining = remaining.removePrefix(spanMatch.value)
        }

        if (remaining.startsWith("<")) { halign = HAlign.LEFT; remaining = remaining.removePrefix("<") }
        else if (remaining.startsWith("^")) { halign = HAlign.CENTER; remaining = remaining.removePrefix("^") }
        else if (remaining.startsWith(">")) { halign = HAlign.RIGHT; remaining = remaining.removePrefix(">") }

        if (remaining.startsWith(".<")) { valign = VAlign.TOP; remaining = remaining.removePrefix(".<") }
        else if (remaining.startsWith(".^")) { valign = VAlign.MIDDLE; remaining = remaining.removePrefix(".^") }
        else if (remaining.startsWith(".>")) { valign = VAlign.BOTTOM; remaining = remaining.removePrefix(".>") }

        if (remaining.startsWith("a|")) remaining = remaining.removePrefix("a|")
        else if (remaining.startsWith("h|")) remaining = remaining.removePrefix("h|")
        else if (remaining.startsWith("|")) remaining = remaining.removePrefix("|")

        return CellPrefix(colSpan, rowSpan, halign, valign, remaining)
    }

    private data class CellPrefix(
        val colSpan: Int,
        val rowSpan: Int,
        val halign: HAlign?,
        val valign: VAlign?,
        val content: String,
    )

    private fun parseTable(lines: List<String>, start: Int, cols: String?): Pair<PivotBlock.Table, Int> {
        var i = start
        if (lines[i].startsWith("[cols=")) i++
        if (i < lines.size && lines[i].startsWith("|===")) i++
        val cellLines = mutableListOf<String>()
        while (i < lines.size && !lines[i].startsWith("|===")) {
            cellLines.add(lines[i])
            i++
        }
        if (i < lines.size) i++

        val rows = parseTableRows(cellLines)
        if (rows.isEmpty()) {
            return PivotBlock.Table(cols, header = emptyList(), rows = emptyList()) to i
        }
        val header = rows[0]
        val bodyRows = rows.drop(1)
        return PivotBlock.Table(cols, header, bodyRows) to i
    }

    private fun parseTableRows(cellLines: List<String>): List<List<List<PivotInline>>> {
        val rows = mutableListOf<List<List<PivotInline>>>()
        var currentRow = mutableListOf<List<PivotInline>>()
        for (line in cellLines) {
            if (line.isBlank()) {
                if (currentRow.isNotEmpty()) {
                    rows.add(currentRow)
                    currentRow = mutableListOf()
                }
                continue
            }
            if (!line.startsWith("|")) {
                if (currentRow.isNotEmpty()) {
                    val lastCell = currentRow.removeAt(currentRow.size - 1)
                    val mergedText = (lastCell.lastOrNull() as? PivotInline.Text)?.text.orEmpty()
                    val rest = lastCell.dropLast(1)
                    val newLastText = if (mergedText.isEmpty()) line.trim() else "$mergedText ${line.trim()}"
                    currentRow.add(rest + listOf(PivotInline.Text(newLastText, translatable = TextTranslatableClassifier.isTranslatable(newLastText))))
                }
                continue
            }
            val cells = splitTableCells(line)
            for (cell in cells) {
                currentRow.add(parseInline(cell.trim()))
            }
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        return rows
    }

    private fun splitTableCells(line: String): List<String> {
        if (!line.startsWith("|")) return listOf(line)
        val result = mutableListOf<String>()
        var current = StringBuilder()
        val content = line.removePrefix("|")
        var inCode = false
        for (ch in content) {
            if (ch == '`') inCode = !inCode
            if (ch == '|' && !inCode) {
                result.add(current.toString())
                current = StringBuilder()
            } else {
                current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun isNumberedListMarker(line: String): Boolean =
        Regex("^\\d+\\.\\s+.+").matches(line)

    private fun isUnorderedListMarker(line: String): Boolean =
        line.startsWith("* ") || line.startsWith("** ") || line.startsWith("*** ")

    private fun listMarkerLength(line: String): Int = when {
        line.startsWith("*** ") -> 4
        line.startsWith("** ") -> 3
        line.startsWith("* ") -> 2
        line.startsWith(". ") -> 2
        isNumberedListMarker(line) -> Regex("^\\d+\\.").find(line)!!.value.length + 1
        else -> 0
    }

    private fun parseList(lines: List<String>, start: Int): Pair<PivotBlock.ListBlock, Int> {
        val firstLine = lines[start]
        val ordered = firstLine.startsWith(". ") || isNumberedListMarker(firstLine)
        val items = mutableListOf<List<PivotInline>>()
        var i = start
        while (i < lines.size && (isUnorderedListMarker(lines[i]) || lines[i].startsWith(". ") || isNumberedListMarker(lines[i]))) {
            val content = lines[i].drop(listMarkerLength(lines[i]))
            items.add(parseInline(content))
            i++
        }
        return PivotBlock.ListBlock(ordered, items) to i
    }

    private fun parseParagraph(lines: List<String>, start: Int): Pair<PivotBlock.Paragraph?, Int> {
        val collectedLines = mutableListOf<String>()
        var i = start
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].startsWith("=") && !isUnorderedListMarker(lines[i]) &&
            !lines[i].startsWith(". ") && !isNumberedListMarker(lines[i]) &&
            !lines[i].startsWith("[") &&
            !lines[i].startsWith("|===") && !lines[i].startsWith("---") &&
            !lines[i].startsWith("----")) {
            collectedLines.add(lines[i].trim())
            i++
        }
        if (collectedLines.isEmpty()) {
            val next = if (i < lines.size) i + 1 else i
            return null to next
        }
        val inline = buildParagraphInlines(collectedLines)
        return PivotBlock.Paragraph(inline) to i
    }

    private fun buildParagraphInlines(lines: List<String>): List<PivotInline> {
        val result = mutableListOf<PivotInline>()
        for ((idx, line) in lines.withIndex()) {
            val isLastLine = idx == lines.size - 1
            val hasLineBreak = line.endsWith("+") && !isLastLine
            val stripped = if (hasLineBreak) line.removeSuffix("+").trimEnd() else line
            if (stripped.isNotEmpty()) {
                if (result.isNotEmpty() && result.last() !is PivotInline.LineBreak) {
                    result.add(PivotInline.Text(" ", true))
                }
                result.addAll(parseInline(stripped))
            }
            if (hasLineBreak) {
                result.add(PivotInline.LineBreak)
            }
        }
        return result
    }

    internal fun parseInline(text: String): List<PivotInline> {
        val segments = mutableListOf<PivotInline>()
        var remaining = text
        while (remaining.isNotEmpty()) {
            val linkWithPrefix = Regex("link:([^\\[]+)\\[([^\\]]+)\\]").find(remaining)
            val directLink = Regex("(https?://[^\\[]+)\\[([^\\]]+)\\]").find(remaining)
            val boldMatch = Regex("\\*\\*([^*]+)\\*\\*").find(remaining)
            val codeMatch = Regex("`([^`]+)`").find(remaining)

            val candidates = listOfNotNull(linkWithPrefix, directLink, boldMatch, codeMatch)
            val nextMatch = candidates.minByOrNull { it.range.first }

            if (nextMatch == null) {
                segments.add(PivotInline.Text(remaining, translatable = TextTranslatableClassifier.isTranslatable(remaining)))
                break
            }

            val prefix = remaining.substring(0, nextMatch.range.first)
            if (prefix.isNotEmpty()) {
                segments.add(PivotInline.Text(prefix, translatable = TextTranslatableClassifier.isTranslatable(prefix)))
            }

            when (nextMatch) {
                linkWithPrefix -> {
                    segments.add(PivotInline.Link(
                        url = nextMatch.groupValues[1],
                        label = nextMatch.groupValues[2],
                        translatable = true
                    ))
                }
                directLink -> {
                    segments.add(PivotInline.Link(
                        url = nextMatch.groupValues[1],
                        label = nextMatch.groupValues[2],
                        translatable = true
                    ))
                }
                boldMatch -> {
                    segments.add(PivotInline.Bold(nextMatch.groupValues[1], translatable = true))
                }
                codeMatch -> {
                    segments.add(PivotInline.Code(nextMatch.groupValues[1], translatable = false))
                }
            }
            remaining = remaining.substring(nextMatch.range.last + 1)
        }
        return segments
    }

    private fun isDescriptionListItem(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.startsWith("//")) return false
        if (trimmed.startsWith("=")) return false
        if (trimmed.startsWith("|")) return false
        if (trimmed.startsWith("* ") || trimmed.startsWith(". ")) return false
        if (isNumberedListMarker(trimmed)) return false
        if (isBlockMacro(trimmed)) return false
        val colonIdx = trimmed.indexOf("::")
        if (colonIdx < 0) return false
        val before = trimmed.substring(0, colonIdx)
        if (before.isBlank()) return false
        if (before.startsWith("[") && !before.contains("]")) return false
        return true
    }

    private fun parseDescriptionList(lines: List<String>, start: Int): Pair<PivotBlock.DescriptionList, Int> {
        val items = mutableListOf<DescriptionItem>()
        var i = start
        while (i < lines.size && isDescriptionListItem(lines[i])) {
            val line = lines[i].trim()
            val colonIdx = line.indexOf("::")
            val termText = line.substring(0, colonIdx).trim()
            val defText = line.substring(colonIdx + 2).trim()
            val term = parseInline(termText)
            val definition = if (defText.isNotEmpty()) parseInline(defText) else emptyList()
            items.add(DescriptionItem(term, definition))
            i++
        }
        return PivotBlock.DescriptionList(items) to i
    }

    private fun isBlockMacro(line: String): Boolean {
        val trimmed = line.trim()
        return Regex("^(image|video)::\\S").containsMatchIn(trimmed)
    }

    private fun parseBlockMacro(line: String): PivotBlock.BlockMacro {
        val trimmed = line.trim()
        val match = Regex("^(image|video)::([^\\[]+)(?:\\[(.*)\\])?$").find(trimmed)
        return if (match != null) {
            PivotBlock.BlockMacro(
                name = match.groupValues[1],
                target = match.groupValues[2],
                attributes = match.groupValues[3]
            )
        } else {
            PivotBlock.BlockMacro(name = "image", target = trimmed.substringAfter("::"))
        }
    }

    companion object {
        private val ADMONITION_KINDS = setOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")
    }
}
