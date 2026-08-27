package document

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookTocParserTest {

    private fun writeToc(content: String): File {
        val file = File.createTempFile("toc-", ".adoc")
        file.writeText(content)
        file.deleteOnExit()
        return file
    }

    @Test
    fun `parse une ligne a page unique en une section`() {
        val toc = writeToc(
            """
            = Livre

            |===
            | Référence | Sujet | Page | Fichier

            | 1.0.1 | Introduction | 5 | 005.pdf
            |===
            """.trimIndent()
        )

        val sections = BookTocParser.parse(toc)

        assertEquals(1, sections.size, "une seule section attendue")
        assertEquals("1.0.1", sections[0].ref)
        assertEquals("Introduction", sections[0].title)
        assertEquals(5, sections[0].page)
        assertEquals("005.pdf", sections[0].pdfFile)
    }

    @Test
    fun `parse une ligne multi-pages en une section par page avec pdf aligne`() {
        val toc = writeToc(
            """
            |===
            | Référence | Sujet | Page | Fichier

            | 1.0.1 | Devenir Formateur | 5, 6, 7, 8 | 005.pdf, 006.pdf, 007.pdf, 008.pdf
            |===
            """.trimIndent()
        )

        val sections = BookTocParser.parse(toc)

        assertEquals(4, sections.size, "quatre sections (une par page)")
        assertEquals(listOf(5, 6, 7, 8), sections.map { it.page })
        assertEquals(listOf("005.pdf", "006.pdf", "007.pdf", "008.pdf"), sections.map { it.pdfFile })
        sections.forEach { assertEquals("Devenir Formateur", it.title) }
        sections.forEach { assertEquals("1.0.1", it.ref) }
    }

    @Test
    fun `parse repete le dernier pdf quand il y a plus de pages que de pdf`() {
        val toc = writeToc(
            """
            |===
            | Référence | Sujet | Page | Fichier

            | 2.2.6 | Dossier Professionnel | 89 | 089.pdf, 090.pdf
            |===
            """.trimIndent()
        )

        val sections = BookTocParser.parse(toc)

        assertEquals(1, sections.size)
        assertEquals(89, sections[0].page)
        assertEquals("089.pdf", sections[0].pdfFile)
    }

    @Test
    fun `parse ignore la ligne d en tete et les lignes vides`() {
        val toc = writeToc(
            """
            [cols="1,3,1,1", options="header"]
            |===
            | Référence | Sujet / Titre de la section | Page | Fichier

            | 1.0.1 | Section A | 5 | 005.pdf

            | 1.0.2 | Section B | 14 | 014.pdf
            |===
            """.trimIndent()
        )

        val sections = BookTocParser.parse(toc)

        assertEquals(2, sections.size)
        assertEquals("1.0.1", sections[0].ref)
        assertEquals("1.0.2", sections[1].ref)
    }

    @Test
    fun `parse retourne une liste vide si le fichier est absent ou vide`() {
        val missing = File("/chemin/inexistant/toc.adoc")
        assertTrue(BookTocParser.parse(missing).isEmpty(), "fichier absent -> vide")

        val empty = writeToc("")
        assertTrue(BookTocParser.parse(empty).isEmpty(), "fichier vide -> vide")
    }

    @Test
    fun `parse ignore les lignes de tableau mal formees (moins de 4 cellules)`() {
        val toc = writeToc(
            """
            |===
            | Référence | Sujet | Page | Fichier

            | 1.0.1 | Section A | 5 | 005.pdf
            | cellule orpheline
            |===
            """.trimIndent()
        )

        val sections = BookTocParser.parse(toc)

        assertEquals(1, sections.size, "la ligne orpheline doit etre ignoree")
    }
}
