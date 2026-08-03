package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AsciiDocParserTableTest {

    private val parser = AsciiDocParser()

    @Test
    fun `parses table with cols spec header and body`() {
        val lines = listOf(
            "[cols=\"1,2,3\"]",
            "|===",
            "| Partition | Taille | Role",
            "",
            "| System A (lecture seule)",
            "| ~8 Go",
            "| Systeme live Xubuntu actif",
            "|===",
        )

        val (table, next) = parser.parseTableStructured(lines, 0, "1,2,3")

        assertEquals(8, next)
        assertEquals(3, table.cols.size)
        assertEquals(1, table.cols[0].width)
        assertEquals(2, table.cols[1].width)
        assertEquals(3, table.cols[2].width)
        assertEquals(1, table.header.size)
        assertEquals(3, table.header[0].cells.size)
        assertEquals("Partition", (table.header[0].cells[0].inline[0] as PivotInline.Text).text)
        assertEquals("Taille", (table.header[0].cells[1].inline[0] as PivotInline.Text).text)
        assertEquals("Role", (table.header[0].cells[2].inline[0] as PivotInline.Text).text)
        assertEquals(1, table.body.size)
        assertEquals(3, table.body[0].cells.size)
        assertEquals("System A (lecture seule)", (table.body[0].cells[0].inline[0] as PivotInline.Text).text)
        assertEquals("~8 Go", (table.body[0].cells[1].inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses table without cols spec`() {
        val lines = listOf(
            "|===",
            "| Partition | Device | Taille",
            "",
            "| System A",
            "| `/dev/sdX1`",
            "| ~8 Go",
            "|===",
        )

        val (table, next) = parser.parseTableStructured(lines, 0, null)

        assertEquals(7, next)
        assertTrue(table.cols.isEmpty())
        assertEquals(1, table.header.size)
        assertEquals(3, table.header[0].cells.size)
        assertEquals(1, table.body.size)
        val code = table.body[0].cells[1].inline[0] as PivotInline.Code
        assertEquals("/dev/sdX1", code.text)
    }

    @Test
    fun `parses table cell with inline code and text`() {
        val lines = listOf(
            "[cols=\"1,3\"]",
            "|===",
            "| Option | Description",
            "",
            "| `-c`, `--clean`",
            "| Nettoyer le repertoire",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,3")

        val cell = table.body[0].cells[0]
        assertEquals(3, cell.inline.size)
        assertEquals("-c", (cell.inline[0] as PivotInline.Code).text)
        assertEquals(", ", (cell.inline[1] as PivotInline.Text).text)
        assertEquals("--clean", (cell.inline[2] as PivotInline.Code).text)
    }

    @Test
    fun `parses escaped pipe in cell content`() {
        val lines = listOf(
            "|===",
            "| Expression | Resultat",
            "",
            "| a \\| b",
            "| pipe preserve",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        val cell = table.body[0].cells[0]
        assertEquals("a | b", (cell.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses cell with col span`() {
        val lines = listOf(
            "[cols=\"1,1,1\"]",
            "|===",
            "| A | B | C",
            "",
            "2+| D spans two columns",
            "| E",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,1,1")

        val cell = table.body[0].cells[0]
        assertEquals(2, cell.colSpan)
        assertEquals(1, cell.rowSpan)
        assertEquals("D spans two columns", (cell.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses cell with row span`() {
        val lines = listOf(
            "[cols=\"1,1\"]",
            "|===",
            "| A | B",
            "",
            ".2+| Spans two rows",
            "| Row 1",
            "",
            "| Row 2",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,1")

        val cell = table.body[0].cells[0]
        assertEquals(1, cell.colSpan)
        assertEquals(2, cell.rowSpan)
        assertEquals("Spans two rows", (cell.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses cell with horizontal alignment`() {
        val lines = listOf(
            "[cols=\"1,1,1\"]",
            "|===",
            "| Left | Center | Right",
            "",
            "<| left aligned",
            "^| centered",
            ">| right aligned",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,1,1")

        assertEquals(HAlign.LEFT, table.body[0].cells[0].halign)
        assertEquals(HAlign.CENTER, table.body[0].cells[1].halign)
        assertEquals(HAlign.RIGHT, table.body[0].cells[2].halign)
    }

    @Test
    fun `parses cell with vertical alignment`() {
        val lines = listOf(
            "[cols=\"1,1,1\"]",
            "|===",
            "| Top | Middle | Bottom",
            "",
            ".<| top aligned",
            ".^| middle aligned",
            ".>| bottom aligned",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,1,1")

        assertEquals(VAlign.TOP, table.body[0].cells[0].valign)
        assertEquals(VAlign.MIDDLE, table.body[0].cells[1].valign)
        assertEquals(VAlign.BOTTOM, table.body[0].cells[2].valign)
    }

    @Test
    fun `parses a| asciidoc style cell`() {
        val lines = listOf(
            "|===",
            "| Normal | AsciiDoc",
            "",
            "| plain text",
            "a| **bold** and `code`",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        val cell = table.body[0].cells[1]
        assertEquals(3, cell.inline.size)
        assertTrue(cell.inline[0] is PivotInline.Bold)
        assertTrue(cell.inline[2] is PivotInline.Code)
    }

    @Test
    fun `parses h| header style cell`() {
        val lines = listOf(
            "|===",
            "| Normal | Header",
            "",
            "| plain text",
            "h| header style",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        val cell = table.body[0].cells[1]
        assertEquals("header style", (cell.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses col spec with alignment`() {
        val lines = listOf(
            "[cols=\"<,^,>\"]",
            "|===",
            "| Left | Center | Right",
            "",
            "| a | b | c",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "<,^,>")

        assertEquals(HAlign.LEFT, table.cols[0].halign)
        assertEquals(HAlign.CENTER, table.cols[1].halign)
        assertEquals(HAlign.RIGHT, table.cols[2].halign)
        assertNull(table.cols[0].width)
    }

    @Test
    fun `parses col spec with width and alignment`() {
        val lines = listOf(
            "[cols=\"<1,^2,>3\"]",
            "|===",
            "| A | B | C",
            "",
            "| x | y | z",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "<1,^2,>3")

        assertEquals(HAlign.LEFT, table.cols[0].halign)
        assertEquals(1, table.cols[0].width)
        assertEquals(HAlign.CENTER, table.cols[1].halign)
        assertEquals(2, table.cols[1].width)
        assertEquals(HAlign.RIGHT, table.cols[2].halign)
        assertEquals(3, table.cols[2].width)
    }

    @Test
    fun `parses col spec with vertical alignment`() {
        val lines = listOf(
            "[cols=\".<,.^,.>\"]",
            "|===",
            "| Top | Middle | Bottom",
            "",
            "| a | b | c",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, ".<,.^,.>")

        assertEquals(VAlign.TOP, table.cols[0].valign)
        assertEquals(VAlign.MIDDLE, table.cols[1].valign)
        assertEquals(VAlign.BOTTOM, table.cols[2].valign)
    }

    @Test
    fun `round-trip parse toAsciiDoc parse is stable`() {
        val originalLines = listOf(
            "[cols=\"1,3\"]",
            "|===",
            "| Option | Description",
            "",
            "| `-c`, `--clean`",
            "| Nettoyer le repertoire",
            "|===",
        )

        val (table1, _) = parser.parseTableStructured(originalLines, 0, "1,3")
        val adoc = table1.toAsciiDoc()
        val (table2, _) = parser.parseTableStructured(adoc.lines(), 0, "1,3")

        assertEquals(table1.cols, table2.cols)
        assertEquals(table1.header.size, table2.header.size)
        assertEquals(table1.body.size, table2.body.size)
        assertEquals(
            (table1.header[0].cells[0].inline[0] as PivotInline.Text).text,
            (table2.header[0].cells[0].inline[0] as PivotInline.Text).text,
        )
        assertEquals(
            (table1.body[0].cells[0].inline[0] as PivotInline.Code).text,
            (table2.body[0].cells[0].inline[0] as PivotInline.Code).text,
        )
    }

    @Test
    fun `parses multi-line cell content`() {
        val lines = listOf(
            "|===",
            "| Cell 1 | Cell 2",
            "",
            "| Single line",
            "| Line one",
            "Line two",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        val cell = table.body[0].cells[1]
        assertEquals("Line one Line two", (cell.inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `parses empty table`() {
        val lines = listOf(
            "|===",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        assertTrue(table.header.isEmpty())
        assertTrue(table.body.isEmpty())
        assertTrue(table.cols.isEmpty())
    }

    @Test
    fun `parses table with bold and link in cells`() {
        val lines = listOf(
            "|===",
            "| Feature | Link",
            "",
            "| **important** feature",
            "| link:https://example.com[docs]",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, null)

        val bold = table.body[0].cells[0].inline[0] as PivotInline.Bold
        assertEquals("important", bold.text)
        val link = table.body[0].cells[1].inline[0] as PivotInline.Link
        assertEquals("https://example.com", link.url)
        assertEquals("docs", link.label)
    }

    @Test
    fun `parses table with multiple body rows`() {
        val lines = listOf(
            "[cols=\"1,3\"]",
            "|===",
            "| Option | Description",
            "",
            "| `-c`",
            "| Clean",
            "",
            "| `-v`",
            "| Verbose",
            "",
            "| `-h`",
            "| Help",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,3")

        assertEquals(1, table.header.size)
        assertEquals(3, table.body.size)
        assertEquals("-c", (table.body[0].cells[0].inline[0] as PivotInline.Code).text)
        assertEquals("-v", (table.body[1].cells[0].inline[0] as PivotInline.Code).text)
        assertEquals("-h", (table.body[2].cells[0].inline[0] as PivotInline.Code).text)
    }

    @Test
    fun `parses table with col span and row span combined`() {
        val lines = listOf(
            "[cols=\"1,1,1\"]",
            "|===",
            "| A | B | C",
            "",
            "2.2+| spans 2 cols 2 rows",
            "| C1",
            "",
            "| C2",
            "|===",
        )

        val (table, _) = parser.parseTableStructured(lines, 0, "1,1,1")

        val cell = table.body[0].cells[0]
        assertEquals(2, cell.colSpan)
        assertEquals(2, cell.rowSpan)
    }
}
