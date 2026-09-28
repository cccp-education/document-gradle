package document.semantic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-SEMANTIC-TABLE — US-1 (DOC-SEMANTIC-1): the pure row projection.
 *
 * A [SemanticRow] maps an abstract `role` to the raw cell text of one table row.
 * The projection itself lives in [SemanticTable.projection] (see
 * [SemanticTableTest]) ; this suite pins the value object contract only.
 */
class SemanticRowTest {

    @Test
    fun `a row keeps the role to value mapping`() {
        val row = SemanticRow(values = mapOf("reference" to "1.0", "title" to "Intro"))

        assertEquals("1.0", row["reference"])
        assertEquals("Intro", row["title"])
    }

    @Test
    fun `a missing role resolves to null`() {
        val row = SemanticRow(values = mapOf("reference" to "1.0"))

        assertEquals(null, row["title"])
    }

    @Test
    fun `roles returns the declared keys in insertion order`() {
        val row = SemanticRow(values = linkedMapOf("reference" to "1.0", "title" to "Intro"))

        assertEquals(listOf("reference", "title"), row.roles)
    }

    @Test
    fun `a blank value is reported as blank`() {
        val row = SemanticRow(values = mapOf("reference" to "1.0", "objective" to "   "))

        assertTrue(row.isBlank("objective"), "whitespace-only value must count as blank")
        assertTrue(!row.isBlank("reference"))
    }

    @Test
    fun `an absent role counts as blank`() {
        val row = SemanticRow(values = mapOf("reference" to "1.0"))

        assertTrue(row.isBlank("objective"), "an absent role must count as blank")
    }

    @Test
    fun `a blank role key is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SemanticRow(values = mapOf("" to "value"))
        }
    }

    @Test
    fun `an empty row is allowed`() {
        // A row whose cells are all empty still projects an (empty) record; the
        // schema-driven projection is what decides if the row is meaningful.
        val row = SemanticRow(values = emptyMap())

        assertTrue(row.roles.isEmpty())
    }
}
