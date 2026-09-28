package document.semantic

import org.asciidoctor.SafeMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * EPIC DOC-SEMANTIC-TABLE — US-2 (DOC-SEMANTIC-2): the [AsciidoctorTableReader]
 * adapter bridging the *real* AsciidoctorJ object model (`org.asciidoctor.ast.*`)
 * to the Gradle-free [SemanticTableReader] port (decision D6).
 *
 * The adapter walks the AST, keeps only the tables annotated opt-in
 * (`[.semantic-<name>]` role or `[semantic=<name>]` block attribute, decision
 * D3) and returns them as [RawTable] values. A non-annotated table is ignored
 * (Économie d'Encre). The domain never sees an AST node.
 *
 * AsciiDoc note: a first row is a *header* only when the table declares the
 * `header` option — the adapter reads the real `Table.header`, it never guesses
 * from the row position.
 */
class AsciidoctorTableReaderTest {

    private val reader = AsciidoctorTableReader(SafeMode.SAFE)

    private fun adoc(build: StringBuilder.() -> Unit): java.io.File {
        val dir = Files.createTempDirectory("doc-semantic-reader-").toFile()
        val file = dir.resolve("doc.adoc")
        file.writeText(StringBuilder().apply(build).toString())
        return file
    }

    @Test
    fun `an annotated table is read with its name and header`() {
        val file = adoc {
            append("[.semantic-planning,options=\"header\"]\n")
            append("|===\n")
            append("| Ref | Titre | Objectif\n\n| 1.0 | Introduction | Situer le cadre\n| 1.1 | Objectifs | Formuler\n")
            append("|===\n")
        }

        val tables = reader.read(file)

        assertEquals(1, tables.size)
        val table = tables.single()
        assertEquals("planning", table.name)
        assertEquals(listOf("Ref", "Titre", "Objectif"), table.header)
        assertEquals(
            listOf(
                listOf("1.0", "Introduction", "Situer le cadre"),
                listOf("1.1", "Objectifs", "Formuler"),
            ),
            table.rows,
        )
    }

    @Test
    fun `a table annotated by block attribute is read too`() {
        val file = adoc {
            append("[semantic=planning,options=\"header\"]\n")
            append("|===\n")
            append("| Ref | Titre\n\n| 1.0 | Introduction\n")
            append("|===\n")
        }

        val tables = reader.read(file)

        assertEquals("planning", tables.single().name)
    }

    @Test
    fun `a non-annotated table is ignored`() {
        val file = adoc {
            append("|===\n")
            append("| A | B\n\n| 1 | 2\n")
            append("|===\n")
        }

        assertTrue(reader.read(file).isEmpty(), "an un-annotated table must never be lifted (D3)")
    }

    @Test
    fun `a document without table yields no raw table`() {
        val file = adoc {
            append("= Title\n\nJust prose, no table here.\n")
        }

        assertTrue(reader.read(file).isEmpty())
    }

    @Test
    fun `several annotated tables keep the document order`() {
        val file = adoc {
            append("[.semantic-first]\n|===\n| A\n\n| 1\n|===\n\n")
            append("[.semantic-second]\n|===\n| B\n\n| 2\n|===\n")
        }

        val tables = reader.read(file)

        assertEquals(listOf("first", "second"), tables.map { it.name })
    }

    @Test
    fun `a table without header option has an empty header`() {
        val file = adoc {
            append("[.semantic-planning]\n")
            append("|===\n")
            append("| 1.0 | Introduction\n")
            append("|===\n")
        }

        val table = reader.read(file).single()

        assertTrue(table.header.isEmpty(), "a table without the header option must expose an empty header")
        assertEquals(listOf(listOf("1.0", "Introduction")), table.rows)
    }

    @Test
    fun `an annotated table nested in a section is still found`() {
        val file = adoc {
            append("== Chapter\n\n")
            append("[.semantic-planning,options=\"header\"]\n|===\n| Ref | Titre\n\n| 1.0 | Introduction\n|===\n")
        }

        val tables = reader.read(file)

        assertEquals("planning", tables.single().name)
        assertEquals(listOf("Ref", "Titre"), tables.single().header)
    }

    @Test
    fun `a mixed document keeps only the annotated tables`() {
        val file = adoc {
            append("|===\n| A | B\n\n| 1 | 2\n|===\n\n")
            append("[.semantic-kept]\n|===\n| K\n\n| v\n|===\n\n")
            append("|===\n| C | D\n\n| 3 | 4\n|===\n")
        }

        val tables = reader.read(file)

        assertEquals(listOf("kept"), tables.map { it.name })
    }
}
