package document.semantic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-SEMANTIC-TABLE — US-1 (DOC-SEMANTIC-1): the sealed extraction result
 * and the pure orchestration over raw tables and injected schemas (decisions
 * D4/D5/D8).
 *
 * [SemanticExtractor] pairs every raw annotated table with the schema the
 * consumer declared under the same name :
 * - a table whose schema is declared is [SemanticExtraction.Extracted] ;
 * - an annotated table with no declared schema is [SemanticExtraction.Skipped]
 *   (never a silent drop — risk #6 mitigation).
 *
 * The required-role rule (D8) is a pure function of the projection : a
 * `required` column with a blank cell yields a [SemanticFinding]. The severity
 * (STRICT/LENIENT/OFF) is applied by the task layer (US-4) — the domain records
 * findings, it never throws.
 */
class SemanticExtractionTest {

    private val raw = RawTable(
        name = "planning",
        header = listOf("Ref", "Titre", "Objectif"),
        rows = listOf(
            listOf("1.0", "Introduction", "Situer le cadre"),
            listOf("1.1", "Objectifs", "   "),
        ),
    )

    private val schema = SemanticTableSchema(
        name = "planning",
        columns = listOf(
            SemanticColumnSpec(index = 0, role = "reference"),
            SemanticColumnSpec(header = "Titre", role = "title"),
            SemanticColumnSpec(header = "Objectif", role = "objective", required = true),
        ),
    )

    @Test
    fun `an annotated table with a declared schema is extracted`() {
        val results = SemanticExtractor.extract(listOf(raw), listOf(schema))

        assertEquals(1, results.size)
        assertTrue(results.single() is SemanticExtraction.Extracted)
        val table = (results.single() as SemanticExtraction.Extracted).table
        assertEquals("planning", table.name)
        assertEquals(2, table.records.size)
    }

    @Test
    fun `an annotated table without a declared schema is skipped with a reason`() {
        val unknown = RawTable(name = "orphan", header = listOf("A"), rows = listOf(listOf("x")))

        val results = SemanticExtractor.extract(listOf(unknown), listOf(schema))

        assertTrue(results.single() is SemanticExtraction.Skipped)
        val skipped = results.single() as SemanticExtraction.Skipped
        assertEquals("orphan", skipped.name)
        assertTrue(skipped.reason.isNotBlank(), "a silently dropped table is forbidden (risk #6)")
    }

    @Test
    fun `the extraction order follows the raw table order`() {
        val second = RawTable(name = "matrix", header = listOf("K"), rows = listOf(listOf("v")))
        val secondSchema = SemanticTableSchema(name = "matrix", columns = listOf(SemanticColumnSpec(index = 0, role = "key")))

        val results = SemanticExtractor.extract(listOf(raw, second), listOf(schema, secondSchema))

        assertEquals(listOf("planning", "matrix"), results.map { it.name() })
    }

    @Test
    fun `a required role left blank yields a finding`() {
        val table = (SemanticExtractor.extract(listOf(raw), listOf(schema)).single() as SemanticExtraction.Extracted).table

        assertEquals(1, table.findings.size)
        val finding = table.findings.single()
        assertEquals("objective", finding.role)
        assertEquals(1, finding.rowIndex, "the blank cell is on the second record (0-based)")
        assertTrue(finding.message.contains("objective"), "the finding must name the offending role")
    }

    @Test
    fun `a fully filled table yields no finding`() {
        val filled = raw.copy(rows = listOf(listOf("1.0", "Introduction", "Situer le cadre")))

        val table = (SemanticExtractor.extract(listOf(filled), listOf(schema)).single() as SemanticExtraction.Extracted).table

        assertTrue(table.findings.isEmpty())
    }

    @Test
    fun `an optional blank role yields no finding`() {
        val optionalSchema = SemanticTableSchema(
            name = "planning",
            columns = listOf(
                SemanticColumnSpec(index = 0, role = "reference"),
                SemanticColumnSpec(header = "Objectif", role = "objective"),
            ),
        )
        val filled = raw.copy(rows = listOf(listOf("1.0", "Introduction", "   ")))

        val table = (SemanticExtractor.extract(listOf(filled), listOf(optionalSchema)).single() as SemanticExtraction.Extracted).table

        assertTrue(table.findings.isEmpty(), "only roles marked required are validated")
    }

    @Test
    fun `findings are ordered by row then schema column order`() {
        val twoColumnSchema = SemanticTableSchema(
            name = "planning",
            columns = listOf(
                SemanticColumnSpec(index = 0, role = "reference", required = true),
                SemanticColumnSpec(header = "Objectif", role = "objective", required = true),
            ),
        )
        val ragged = RawTable(
            name = "planning",
            header = listOf("Ref", "Objectif"),
            rows = listOf(
                listOf("   ", "   "),
                listOf("1.1", "   "),
            ),
        )

        val table = (SemanticExtractor.extract(listOf(ragged), listOf(twoColumnSchema)).single() as SemanticExtraction.Extracted).table

        assertEquals(listOf(0 to "reference", 0 to "objective", 1 to "objective"), table.findings.map { it.rowIndex to it.role })
    }

    @Test
    fun `no schema at all yields an empty extraction`() {
        val results = SemanticExtractor.extract(listOf(raw), emptyList())

        assertTrue(results.single() is SemanticExtraction.Skipped)
    }

    private fun SemanticExtraction.name(): String = when (this) {
        is SemanticExtraction.Extracted -> table.name
        is SemanticExtraction.Skipped -> name
    }
}
