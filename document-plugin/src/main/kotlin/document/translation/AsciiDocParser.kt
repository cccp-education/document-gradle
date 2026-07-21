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
                line.startsWith("[source") || line.startsWith("[plantuml]") -> {
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
            header.startsWith("[plantuml]") -> "plantuml"
            else -> ""
        }
        var i = start + 1
        while (i < lines.size && lines[i].trim() != "----") i++
        if (i >= lines.size) {
            return PivotBlock.Source(language, "") to i
        }
        i++
        val contentStart = i
        while (i < lines.size && !isSourceClosingDelimiter(lines[i])) i++
        val content = lines.subList(contentStart, i).joinToString("\n")
        if (i < lines.size) i++
        return PivotBlock.Source(language, content) to i
    }

    private fun isSourceClosingDelimiter(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed == "----") return true
        if (trimmed == "====") return true
        return false
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
        return PivotBlock.Source(language, content) to i
    }

    private fun parseAdmonition(lines: List<String>, start: Int, kind: String): Pair<PivotBlock.Admonition, Int> {
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

    private fun parseInline(text: String): List<PivotInline> {
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

    companion object {
        private val ADMONITION_KINDS = setOf("NOTE", "TIP", "IMPORTANT", "WARNING", "CAUTION")
    }
}
