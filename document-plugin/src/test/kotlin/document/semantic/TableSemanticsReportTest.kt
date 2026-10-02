package document.semantic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-SEMANTIC-TABLE — US-3: the [TableSemanticsReport] export value
 * (`table-semantics.json`, decision D7).
 *
 * The report is a pure serialisable value built from the [SemanticExtraction]
 * list the domain already produced — it never re-reads the AST (Ink Economy
 * Law). Assertions cover the shape, the determinism and the JSON export.
 */
class TableSemanticsReportTest {

    private val schema = SemanticTableSchema(
        name = "planning",
        columns = listOf(
            SemanticColumnSpec.byIndex(0, "reference"),
            SemanticColumnSpec.byHeader("Title", "title"),
            SemanticColumnSpec.byIndex(2, "objective", required = true),
        ),
    )

    private fun raw(vararg rows: List<String>) = RawTable(
        name = "planning",
        header = listOf("Ref", "Title", "Objectif"),
        rows = rows.toList(),
    )

    @Test
    fun `an extracted table is exported with its records and findings`() {
        val extractions = SemanticExtractor.extract(
            listOf(raw(listOf("1.0", "Intro", "Situer"))),
            listOf(schema),
        )

        val report = TableSemanticsReport.fromExtractions(TableSemanticsMode.LENIENT, listOf(schema), extractions)

        assertEquals("LENIENT", report.mode)
        assertEquals(listOf("planning"), report.schemas)
        assertEquals(1, report.tables.size)
        val table = report.tables.single()
        assertEquals("planning", table.name)
        assertEquals(
            listOf(mapOf("reference" to "1.0", "title" to "Intro", "objective" to "Situer")),
            table.records,
        )
        assertTrue(table.findings.isEmpty())
    }

    @Test
    fun `a blank required role produces a finding in the report`() {
        val extractions = SemanticExtractor.extract(
            listOf(raw(listOf("1.0", "Intro", ""))),
            listOf(schema),
        )

        val report = TableSemanticsReport.fromExtractions(TableSemanticsMode.STRICT, listOf(schema), extractions)

        assertEquals(1, report.findings.size)
        val finding = report.findings.single()
        assertEquals("planning", finding.table)
        assertEquals(0, finding.rowIndex)
        assertEquals("objective", finding.role)
    }

    @Test
    fun `an annotated table without a declared schema is kept as skipped`() {
        val orphan = RawTable(name = "unknown", rows = listOf(listOf("x")))
        val extractions = SemanticExtractor.extract(listOf(orphan), listOf(schema))

        val report = TableSemanticsReport.fromExtractions(TableSemanticsMode.LENIENT, listOf(schema), extractions)

        assertTrue(report.tables.isEmpty())
        assertEquals(1, report.skipped.size)
        assertEquals("unknown", report.skipped.single().name)
        assertTrue(report.skipped.single().reason.contains("no schema"))
    }

    @Test
    fun `the record map keeps the schema declaration order`() {
        val extractions = SemanticExtractor.extract(
            listOf(raw(listOf("1.0", "Intro", "Situer"))),
            listOf(schema),
        )

        val record = TableSemanticsReport.fromExtractions(TableSemanticsMode.LENIENT, listOf(schema), extractions)
            .tables.single().records.single()

        assertEquals(listOf("reference", "title", "objective"), record.keys.toList())
    }

    @Test
    fun `the empty report carries the mode and nothing else`() {
        val report = TableSemanticsReport.empty(TableSemanticsMode.OFF)

        assertEquals("OFF", report.mode)
        assertTrue(report.schemas.isEmpty())
        assertTrue(report.tables.isEmpty())
        assertTrue(report.skipped.isEmpty())
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `toJson serialises the records and the mode`() {
        val extractions = SemanticExtractor.extract(
            listOf(raw(listOf("1.0", "Intro", "Situer"))),
            listOf(schema),
        )

        val json = TableSemanticsReport.fromExtractions(TableSemanticsMode.LENIENT, listOf(schema), extractions).toJson()

        assertTrue(json.contains("\"mode\" : \"LENIENT\""), "the mode must be serialised")
        assertTrue(json.contains("\"planning\""), "the table name must be serialised")
        assertTrue(json.contains("\"reference\"") && json.contains("\"1.0\""), "a record value must be serialised")
        assertFalse(json.contains("null"), "NON_NULL inclusion drops absent optional fields")
    }
}
