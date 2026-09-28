package document.semantic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-SEMANTIC-TABLE — US-1 (DOC-SEMANTIC-1): the pure `document.semantic`
 * domain (decision D5).
 *
 * A [SemanticColumnSpec] is *abstract*: it targets a column by index or by
 * header text and names an opaque `role`; the plugin public repo never knows a
 * business word (decision D4, gate OSS-clean). A [SemanticTableSchema] groups
 * the column specs under a name. Both are pure values — no Gradle, no
 * AsciidoctorJ — so they are fully unit-testable in isolation.
 */
class SemanticTableSchemaTest {

    @Test
    fun `a column targets a zero-based index`() {
        val column = SemanticColumnSpec(index = 2, role = "objective")

        assertEquals(2, column.index)
        assertEquals("objective", column.role)
        assertTrue(!column.required, "required must default to false")
    }

    @Test
    fun `a column can target a header text instead of an index`() {
        val column = SemanticColumnSpec(header = "Title", role = "title")

        assertEquals("Title", column.header)
        assertEquals("title", column.role)
    }

    @Test
    fun `a blank role is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticColumnSpec(index = 0, role = "   ")
        }
    }

    @Test
    fun `a column without index nor header is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticColumnSpec(role = "reference")
        }
    }

    @Test
    fun `a negative index is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticColumnSpec(index = -1, role = "reference")
        }
    }

    @Test
    fun `byIndex builds an index-targeted column`() {
        val column = SemanticColumnSpec.byIndex(1, role = "duration", required = true)

        assertEquals(1, column.index)
        assertEquals("duration", column.role)
        assertTrue(column.required)
    }

    @Test
    fun `byHeader builds a header-targeted column`() {
        val column = SemanticColumnSpec.byHeader("Objectif", role = "objective")

        assertEquals("Objectif", column.header)
        assertEquals("objective", column.role)
    }

    @Test
    fun `a schema exposes its name and columns`() {
        val schema = SemanticTableSchema(
            name = "planning",
            columns = listOf(
                SemanticColumnSpec(index = 0, role = "reference"),
                SemanticColumnSpec(header = "Title", role = "title"),
            ),
        )

        assertEquals("planning", schema.name)
        assertEquals(listOf("reference", "title"), schema.columns.map { it.role })
    }

    @Test
    fun `a blank schema name is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticTableSchema(name = "", columns = listOf(SemanticColumnSpec(index = 0, role = "a")))
        }
    }

    @Test
    fun `a schema without columns is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticTableSchema(name = "planning", columns = emptyList())
        }
    }

    @Test
    fun `a duplicated role is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticTableSchema(
                name = "planning",
                columns = listOf(
                    SemanticColumnSpec(index = 0, role = "reference"),
                    SemanticColumnSpec(index = 1, role = "reference"),
                ),
            )
        }
    }
}
