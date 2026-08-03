package document.translation.validation

import document.translation.Cell
import document.translation.ColSpec
import document.translation.HAlign
import document.translation.PivotInline
import document.translation.Row
import document.translation.Table
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TableSyntaxValidatorTest {

    @Test
    fun `valid table with cols matching header and body returns Valid`() {
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
                        Cell(listOf(PivotInline.Text("--clean", translatable = true))),
                        Cell(listOf(PivotInline.Text("Clean build", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "My Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `valid table without cols returns Valid`() {
        val table = Table(
            cols = emptyList(),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                        Cell(listOf(PivotInline.Text("2", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `valid table with no header returns Valid`() {
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

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `empty table returns Valid`() {
        val table = Table(cols = emptyList(), header = emptyList(), body = emptyList())

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `cols count mismatch with header returns Invalid`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3), ColSpec(width = 2)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                        Cell(listOf(PivotInline.Text("2", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "My Article", 2)

        assertIs<TableValidationResult.Invalid>(result)
        assertEquals("My Article", result.articleTitle)
        assertEquals(2, result.tableIndex)
        assertTrue(result.reason.contains("cols count"))
        assertTrue(result.reason.contains("header column count"))
    }

    @Test
    fun `cols count mismatch with body row returns Invalid`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 3)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                        Cell(listOf(PivotInline.Text("2", translatable = true))),
                        Cell(listOf(PivotInline.Text("3", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("body row 0"))
    }

    @Test
    fun `column count mismatch between header and body returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("column count mismatch"))
    }

    @Test
    fun `delimiter in cell content returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Header", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("content |=== broken", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("|==="))
    }

    @Test
    fun `delimiter in header cell returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Header |=== bad", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("ok", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("|==="))
    }

    @Test
    fun `delimiter in bold inline returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Bold("text |=== bad", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("|==="))
    }

    @Test
    fun `delimiter in code inline returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Code("|=== delimiter", translatable = false))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("|==="))
    }

    @Test
    fun `delimiter in link label returns Invalid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Link("https://example.com", "click |=== here", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Invalid>(result)
        assertTrue(result.reason.contains("|==="))
    }

    @Test
    fun `single cell table returns Valid`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Only", translatable = true))),
                    ),
                ),
            ),
            body = emptyList(),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `multiple body rows with consistent columns returns Valid`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 1), ColSpec(width = 1)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                        Cell(listOf(PivotInline.Text("C", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                        Cell(listOf(PivotInline.Text("2", translatable = true))),
                        Cell(listOf(PivotInline.Text("3", translatable = true))),
                    ),
                ),
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("4", translatable = true))),
                        Cell(listOf(PivotInline.Text("5", translatable = true))),
                        Cell(listOf(PivotInline.Text("6", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `cols with alignment spec returns Valid`() {
        val table = Table(
            cols = listOf(ColSpec(halign = HAlign.CENTER), ColSpec(halign = HAlign.RIGHT)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("Centered", translatable = true))),
                        Cell(listOf(PivotInline.Text("Right", translatable = true))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("a", translatable = true))),
                        Cell(listOf(PivotInline.Text("b", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `table with mixed inline types and no delimiter returns Valid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
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
                        Cell(
                            listOf(
                                PivotInline.Bold("important", translatable = true),
                                PivotInline.Text(" note", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `table with link inline and no delimiter returns Valid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Link("https://example.com", "docs", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `table with LineBreak inline returns Valid`() {
        val table = Table(
            cols = emptyList(),
            header = emptyList(),
            body = listOf(
                Row(
                    listOf(
                        Cell(
                            listOf(
                                PivotInline.Text("line1", translatable = true),
                                PivotInline.LineBreak,
                                PivotInline.Text("line2", translatable = true),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `header empty cell logs warning but returns Valid`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1), ColSpec(width = 1)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("OK", translatable = true))),
                        Cell(listOf(PivotInline.Text("", translatable = false))),
                    ),
                ),
            ),
            body = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("1", translatable = true))),
                        Cell(listOf(PivotInline.Text("2", translatable = true))),
                    ),
                ),
            ),
        )

        val result = TableSyntaxValidator.validate(table, "Article", 0)

        assertIs<TableValidationResult.Valid>(result)
    }

    @Test
    fun `articleTitle and tableIndex are propagated in Invalid result`() {
        val table = Table(
            cols = listOf(ColSpec(width = 1)),
            header = listOf(
                Row(
                    listOf(
                        Cell(listOf(PivotInline.Text("A", translatable = true))),
                        Cell(listOf(PivotInline.Text("B", translatable = true))),
                    ),
                ),
            ),
            body = emptyList(),
        )

        val result = TableSyntaxValidator.validate(table, "Specific Article", 5)

        assertIs<TableValidationResult.Invalid>(result)
        assertEquals("Specific Article", result.articleTitle)
        assertEquals(5, result.tableIndex)
    }
}
