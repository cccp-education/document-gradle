package document.semantic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-SEMANTIC-TABLE — US-1 (DOC-SEMANTIC-1): the pure projection of a raw
 * annotated table ([RawTable]) into typed records ([SemanticTable]).
 *
 * The schema is *injected* (decision D4) : [SemanticTable.projection] maps every
 * raw cell onto its declared role — a column is addressed by its zero-based
 * `index` or by its `header` text (case-insensitive). Columns of the raw table
 * that are not declared in the schema are simply not projected (Économie
 * d'Encre). The projection is deterministic and free of I/O.
 */
class SemanticTableTest {

    private val raw = RawTable(
        name = "planning",
        header = listOf("Ref", "Titre", "Objectif", "Durée"),
        rows = listOf(
            listOf("1.0", "Introduction", "Situer le cadre", "30 min"),
            listOf("1.1", "Objectifs", "Formuler les objectifs", "45 min"),
        ),
    )

    private val schema = SemanticTableSchema(
        name = "planning",
        columns = listOf(
            SemanticColumnSpec(index = 0, role = "reference"),
            SemanticColumnSpec(header = "Titre", role = "title"),
            SemanticColumnSpec(header = "Objectif", role = "objective", required = true),
            SemanticColumnSpec(index = 3, role = "duration"),
        ),
    )

    @Test
    fun `projection maps every row to a record keyed by role`() {
        val table = SemanticTable.projection(raw, schema)

        assertEquals(2, table.records.size)
        val first = table.records.first()
        assertEquals("1.0", first["reference"])
        assertEquals("Introduction", first["title"])
        assertEquals("Situer le cadre", first["objective"])
        assertEquals("30 min", first["duration"])
    }

    @Test
    fun `projection keeps the schema order of roles`() {
        val table = SemanticTable.projection(raw, schema)

        assertEquals(listOf("reference", "title", "objective", "duration"), table.records.first().roles)
    }

    @Test
    fun `projection resolves a header column case-insensitively`() {
        val uppercaseHeader = SemanticTableSchema(
            name = "planning",
            columns = listOf(SemanticColumnSpec(header = "TITRE", role = "title")),
        )
        val table = SemanticTable.projection(raw, uppercaseHeader)

        assertEquals("Introduction", table.records.first()["title"])
    }

    @Test
    fun `projection ignores columns absent from the schema`() {
        val narrow = SemanticTableSchema(
            name = "planning",
            columns = listOf(SemanticColumnSpec(index = 0, role = "reference")),
        )
        val table = SemanticTable.projection(raw, narrow)

        assertEquals(listOf("reference"), table.records.first().roles)
    }

    @Test
    fun `projection tolerates a row shorter than the header`() {
        val ragged = RawTable(
            name = "planning",
            header = listOf("Ref", "Titre", "Objectif"),
            rows = listOf(listOf("1.0", "Introduction")),
        )
        val narrow = SemanticTableSchema(
            name = "planning",
            columns = listOf(
                SemanticColumnSpec(index = 0, role = "reference"),
                SemanticColumnSpec(index = 1, role = "title"),
                SemanticColumnSpec(index = 2, role = "objective"),
            ),
        )
        val table = SemanticTable.projection(ragged, narrow)

        assertEquals("1.0", table.records.first()["reference"])
        assertEquals("Introduction", table.records.first()["title"])
        assertEquals("", table.records.first()["objective"], "a missing cell must project as blank, not crash")
    }

    @Test
    fun `projection of a table without header resolves index-targeted columns only`() {
        val headerless = RawTable(
            name = "planning",
            header = emptyList(),
            rows = listOf(listOf("1.0", "Introduction")),
        )
        val indexOnly = SemanticTableSchema(
            name = "planning",
            columns = listOf(SemanticColumnSpec(index = 0, role = "reference")),
        )
        val table = SemanticTable.projection(headerless, indexOnly)

        assertEquals("1.0", table.records.first()["reference"])
    }

    @Test
    fun `table exposes its name and the underlying raw table`() {
        val table = SemanticTable.projection(raw, schema)

        assertEquals("planning", table.name)
        assertEquals(raw, table.raw)
    }

    @Test
    fun `a table without records is valid`() {
        val empty = RawTable(name = "planning", header = listOf("Ref"), rows = emptyList())
        val table = SemanticTable.projection(empty, schema.copy(columns = listOf(SemanticColumnSpec(index = 0, role = "reference"))))

        assertTrue(table.records.isEmpty())
    }
}
