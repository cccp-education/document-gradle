package document.translation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableTest {

    @Test
    fun `extractTranslatable returns only translatable Text nodes`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Option", translatable = true))),
                        Cell(listOf(PivotInline.Text("Description", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("-c", translatable = false),
                                PivotInline.Text(", ", translatable = false),
                                PivotInline.Code("--clean", translatable = false),
                            ),
                        ),
                        Cell(listOf(PivotInline.Text("Nettoyer le repertoire", translatable = true))),
                    ),
                ),
            ),
        )

        val segments = table.extractTranslatable()

        assertEquals(3, segments.size)
        assertEquals("Option", segments[0].text)
        assertEquals(0, segments[0].rowIndex)
        assertEquals(0, segments[0].cellIndex)
        assertEquals("Description", segments[1].text)
        assertEquals(0, segments[1].rowIndex)
        assertEquals(1, segments[1].cellIndex)
        assertEquals("Nettoyer le repertoire", segments[2].text)
        assertEquals(1, segments[2].rowIndex)
        assertEquals(1, segments[2].cellIndex)
    }

    @Test
    fun `extractTranslatable skips non-Text nodes and non-translatable Text`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("code", translatable = false),
                                PivotInline.Bold("bold", translatable = true),
                                PivotInline.Text("text", translatable = false),
                                PivotInline.Text("translatable", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val segments = table.extractTranslatable()

        assertEquals(1, segments.size)
        assertEquals("translatable", segments[0].text)
    }

    @Test
    fun `reinject round-trip preserves structure`() {
        val original = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Option", translatable = true))),
                        Cell(listOf(PivotInline.Text("Description", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("-c", translatable = false),
                                PivotInline.Text(", ", translatable = false),
                                PivotInline.Code("--clean", translatable = false),
                            ),
                        ),
                        Cell(listOf(PivotInline.Text("Clean build dir", translatable = true))),
                    ),
                ),
            ),
        )

        val segments = original.extractTranslatable()
        val translations = segments.mapIndexed { idx, seg ->
            val key = (seg.rowIndex * 10000) + (seg.cellIndex * 100) + seg.inlineIndex
            key to "TRANSLATED_$idx"
        }.toMap()

        val result = original.reinject(translations)

        assertEquals("TRANSLATED_0", (result.header[0].cells[0].inline[0] as PivotInline.Text).text)
        assertEquals("TRANSLATED_1", (result.header[0].cells[1].inline[0] as PivotInline.Text).text)
        assertEquals("-c", (result.body[0].cells[0].inline[0] as PivotInline.Code).text)
        assertEquals(", ", (result.body[0].cells[0].inline[1] as PivotInline.Text).text)
        assertEquals("--clean", (result.body[0].cells[0].inline[2] as PivotInline.Code).text)
        assertEquals("TRANSLATED_2", (result.body[0].cells[1].inline[0] as PivotInline.Text).text)
    }

    @Test
    fun `reinject does not modify non-translatable nodes`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("unchanged", translatable = false),
                                PivotInline.Text("unchanged", translatable = false),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = table.reinject(emptyMap())

        assertEquals("unchanged", (result.body[0].cells[0].inline[0] as PivotInline.Code).text)
        assertEquals("unchanged", (result.body[0].cells[0].inline[1] as PivotInline.Text).text)
    }

    @Test
    fun `toAsciiDoc produces valid AsciiDoc table with cols`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Option", translatable = true))),
                        Cell(listOf(PivotInline.Text("Description", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("-c", translatable = false),
                                PivotInline.Text(", ", translatable = false),
                                PivotInline.Code("--clean", translatable = false),
                            ),
                        ),
                        Cell(listOf(PivotInline.Text("Clean build", translatable = true))),
                    ),
                ),
            ),
        )

        val adoc = table.toAsciiDoc()

        assertTrue(adoc.contains("[cols=\"1,3\"]"))
        assertTrue(adoc.contains("|==="))
        assertTrue(adoc.contains("|Option "))
        assertTrue(adoc.contains("|Description "))
        assertTrue(adoc.contains("|`-c`, `--clean` "))
        assertTrue(adoc.contains("|Clean build "))
    }

    @Test
    fun `toAsciiDoc produces table without cols when empty`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
        )

        val adoc = table.toAsciiDoc()

        assertFalse(adoc.contains("[cols="))
        assertTrue(adoc.contains("|==="))
        assertTrue(adoc.contains("|A "))
        assertTrue(adoc.contains("|B "))
    }

    @Test
    fun `toAsciiDoc round-trip is byte-identical`() {
        val original = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Option", translatable = true))),
                        Cell(listOf(PivotInline.Text("Description", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("-c", translatable = false),
                                PivotInline.Text(", ", translatable = false),
                                PivotInline.Code("--clean", translatable = false),
                            ),
                        ),
                        Cell(listOf(PivotInline.Text("Clean build", translatable = true))),
                    ),
                ),
            ),
        )

        val adoc = original.toAsciiDoc()
        val parser = AsciiDocParser()
        val (parsed, _) = parser.parseTableStructured(adoc.lines(), 0, "1,3")

        assertEquals(original.cols, parsed.cols)
        assertEquals(original.header.size, parsed.header.size)
        assertEquals(original.body.size, parsed.body.size)
        assertEquals(
            (original.header[0].cells[0].inline[0] as PivotInline.Text).text,
            (parsed.header[0].cells[0].inline[0] as PivotInline.Text).text,
        )
    }

    @Test
    fun `toHtml produces valid HTML table`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Option", translatable = true))),
                        Cell(listOf(PivotInline.Text("Description", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Code("-c", translatable = false),
                                PivotInline.Text(", ", translatable = false),
                                PivotInline.Code("--clean", translatable = false),
                            ),
                        ),
                        Cell(listOf(PivotInline.Text("Clean build", translatable = true))),
                    ),
                ),
            ),
        )

        val html = table.toHtml()

        assertTrue(html.contains("<table>"))
        assertTrue(html.contains("<thead>"))
        assertTrue(html.contains("<th>Option</th>"))
        assertTrue(html.contains("<th>Description</th>"))
        assertTrue(html.contains("<tbody>"))
        assertTrue(html.contains("<td><code>-c</code>, <code>--clean</code></td>"))
        assertTrue(html.contains("<td>Clean build</td>"))
        assertTrue(html.contains("</table>"))
    }

    @Test
    fun `toHtml produces table without thead when no header`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
        )

        val html = table.toHtml()

        assertFalse(html.contains("<thead>"))
        assertTrue(html.contains("<tbody>"))
        assertTrue(html.contains("<td>A</td>"))
        assertTrue(html.contains("<td>B</td>"))
    }

    @Test
    fun `toHtml renders bold and link inline`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Bold("important", translatable = true),
                                PivotInline.Text(" see ", translatable = true),
                                PivotInline.Link("https://example.com", "docs", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val html = table.toHtml()

        assertTrue(html.contains("<strong>important</strong>"))
        assertTrue(html.contains("<a href=\"https://example.com\">docs</a>"))
    }

    @Test
    fun `cell with colSpan and rowSpan produces correct HTML attrs`() {
        val cell = Cell(
            inline = listOf(PivotInline.Text("spanned", translatable = true)),
            colSpan = 2,
            rowSpan = 3,
        )

        val attrs = cell.htmlAttrs()

        assertTrue(attrs.contains("colspan=\"2\""))
        assertTrue(attrs.contains("rowspan=\"3\""))
    }

    @Test
    fun `cell with alignment produces correct HTML style`() {
        val cell = Cell(
            inline = listOf(PivotInline.Text("centered", translatable = true)),
            halign = HAlign.CENTER,
        )

        val attrs = cell.htmlAttrs()

        assertTrue(attrs.contains("text-align: center"))
    }

    @Test
    fun `ColSpec parses width only`() {
        val spec = ColSpec(width = 3)

        assertEquals("3", spec.toAsciiDoc())
    }

    @Test
    fun `ColSpec parses alignment only`() {
        val spec = ColSpec(halign = HAlign.CENTER)

        assertEquals("^", spec.toAsciiDoc())
    }

    @Test
    fun `ColSpec parses width and alignment`() {
        val spec = ColSpec(width = 2, halign = HAlign.LEFT)

        assertEquals("<2", spec.toAsciiDoc())
    }

    @Test
    fun `ColSpec parses vertical alignment`() {
        val spec = ColSpec(valign = VAlign.TOP)

        assertEquals(".<", spec.toAsciiDoc())
    }

    @Test
    fun `empty table produces empty AsciiDoc`() {
        val table = Table(cols = emptyList(), header = emptyList(), body = emptyList())

        val adoc = table.toAsciiDoc()

        assertEquals("|===\n|===\n", adoc)
    }

    @Test
    fun `empty table produces empty HTML`() {
        val table = Table(cols = emptyList(), header = emptyList(), body = emptyList())

        val html = table.toHtml()

        assertEquals("<table>\n</table>\n", html)
    }

    @Test
    fun `extractTranslatable on empty table returns empty list`() {
        val table = Table(cols = emptyList(), header = emptyList(), body = emptyList())

        val segments = table.extractTranslatable()

        assertTrue(segments.isEmpty())
    }
}
