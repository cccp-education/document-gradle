package document.translation

import document.translation.AsciiDocParser
import document.translation.Cell
import document.translation.ColSpec
import document.translation.HAlign
import document.translation.PivotInline
import document.translation.Row
import document.translation.Table
import document.translation.TranslatableSegment
import io.cucumber.java.en.And
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TableTranslationSteps {

    private var table: Table? = null
    private var segments: List<TranslatableSegment> = emptyList()
    private var asciiDocOutput: String = ""
    private var htmlOutput: String = ""

    @Given("a table with cols {string} and header {string}")
    fun `a table with cols and header`(cols: String, header: String) {
        val colSpecs = if (cols.isBlank()) emptyList() else cols.split(",").map { ColSpec(width = it.toIntOrNull()) }
        val headerCells = header.split(",").map { Cell(listOf(PivotInline.Text(it, translatable = true))) }
        table = Table(colSpecs, listOf(Row(headerCells)), emptyList())
    }

    @And("a body row with cells {string} and {string}")
    fun `a body row with cells`(cell1: String, cell2: String) {
        val c1 = parseCell(cell1)
        val c2 = parseCell(cell2)
        table = table!!.copy(body = listOf(Row(listOf(c1, c2))))
    }

    @And("a body row with cells {string}, {string} and {string}")
    fun `a body row with three cells`(cell1: String, cell2: String, cell3: String) {
        val c1 = parseCell(cell1)
        val c2 = parseCell(cell2)
        val c3 = parseCell(cell3)
        table = table!!.copy(body = listOf(Row(listOf(c1, c2, c3))))
    }

    private fun parseCell(raw: String): Cell {
        val parser = AsciiDocParser()
        val lines = listOf("|===", "| $raw", "|===")
        val (parsed, _) = parser.parseTableStructured(lines, 0, null)
        val allRows = parsed.header + parsed.body
        return allRows.firstOrNull()?.cells?.firstOrNull()
            ?: Cell(listOf(PivotInline.Text(raw, translatable = true)))
    }

    private fun parseCellContent(raw: String): List<PivotInline> {
        val parser = AsciiDocParser()
        val stripped = raw
            .replace(Regex("^(\\d+)?\\+?(\\.(\\d+)\\+)?[<^>]?(\\.[<^>])?[ah]?\\|"), "")
        return parser.parseInline(stripped)
    }

    @Given("an empty table")
    fun `an empty table`() {
        table = Table(emptyList(), emptyList(), emptyList())
    }

    @When("I extract translatable segments")
    fun `i extract translatable segments`() {
        segments = table!!.extractTranslatable()
    }

    @Then("I should get {int} segments")
    fun `i should get segments`(count: Int) {
        assertEquals(count, segments.size)
    }

    @And("segment {int} should have text {string}")
    fun `segment should have text`(index: Int, text: String) {
        assertTrue(index < segments.size, "Segment $index not found, only ${segments.size} segments")
        assertEquals(text, segments[index].text)
    }

    @When("I translate all segments with prefix {string}")
    fun `i translate all segments with prefix`(prefix: String) {
        val translations = segments.mapIndexed { idx, seg ->
            val key = (seg.rowIndex * 10000) + (seg.cellIndex * 100) + seg.inlineIndex
            key to "$prefix${seg.text}"
        }.toMap()
        table = table!!.reinject(translations)
    }

    @And("I reinject the translations")
    fun `i reinject the translations`() {
    }

    @Then("cell {int},{int} should contain text {string}")
    fun `cell should contain text`(row: Int, col: Int, text: String) {
        val allRows = table!!.header + table!!.body
        val cell = allRows[row].cells[col]
        val hasText = cell.inline.any { it is PivotInline.Text && it.text == text }
        assertTrue(hasText, "Cell $row,$col should contain text '$text' but has ${cell.inline}")
    }

    @Then("cell {int},{int} should contain code {string}")
    fun `cell should contain code`(row: Int, col: Int, code: String) {
        val allRows = table!!.header + table!!.body
        val cell = allRows[row].cells[col]
        val hasCode = cell.inline.any { it is PivotInline.Code && it.text == code }
        assertTrue(hasCode, "Cell $row,$col should contain code '$code' but has ${cell.inline}")
    }

    @When("I render the table to AsciiDoc")
    fun `i render the table to asciidoc`() {
        asciiDocOutput = table!!.toAsciiDoc()
    }

    @Then("the AsciiDoc output should contain {string}")
    fun `the asciidoc output should contain`(expected: String) {
        assertTrue(asciiDocOutput.contains(expected), "AsciiDoc output should contain '$expected' but was:\n$asciiDocOutput")
    }

    @Then("the AsciiDoc output should not contain {string}")
    fun `the asciidoc output should not contain`(unexpected: String) {
        assertTrue(!asciiDocOutput.contains(unexpected), "AsciiDoc output should NOT contain '$unexpected' but was:\n$asciiDocOutput")
    }

    @When("I render the table to HTML")
    fun `i render the table to html`() {
        htmlOutput = table!!.toHtml()
    }

    @Then("the HTML output should contain {string}")
    fun `the html output should contain`(expected: String) {
        assertTrue(htmlOutput.contains(expected), "HTML output should contain '$expected' but was:\n$htmlOutput")
    }

    @Then("cell {int},{int} should have colspan {string}")
    fun `cell should have colspan`(row: Int, col: Int, colspan: String) {
        val allRows = table!!.header + table!!.body
        val cell = allRows[row].cells[col]
        assertEquals(colspan.toInt(), cell.colSpan)
    }

    @Then("cell {int},{int} should have style {string}")
    fun `cell should have style`(row: Int, col: Int, style: String) {
        val allRows = table!!.header + table!!.body
        val cell = allRows[row].cells[col]
        val attrs = cell.htmlAttrs()
        assertTrue(attrs.contains(style), "Cell $row,$col should have style '$style' but attrs are: $attrs")
    }

    @Then("the output should be {string}")
    fun `the output should be`(expected: String) {
        assertEquals(expected, asciiDocOutput)
    }

    @Then("the HTML output should be {string}")
    fun `the html output should be`(expected: String) {
        assertEquals(expected, htmlOutput)
    }
}
