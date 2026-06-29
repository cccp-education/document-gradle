package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertThrows
import java.io.File
import java.nio.file.Files

class DocumentSourceTest {

    private fun tempFile(name: String, content: String): File {
        val dir = Files.createTempDirectory("doc-test").toFile()
        return File(dir, name).apply { writeText(content) }
    }

    @Test
    fun `DocumentSource extrait le nom sans extension`() {
        val file = tempFile("livre.adoc", "= Livre")
        val source = DocumentSource(file)

        assertEquals("livre", source.name)
        assertEquals("= Livre", source.readText())
    }

    @Test
    fun `DocumentSource refuse un fichier non adoc`() {
        val file = tempFile("doc.txt", "texte")

        val ex = assertThrows(IllegalArgumentException::class.java) {
            DocumentSource(file)
        }
        assertTrue(ex.message!!.contains(".adoc"))
    }
}

class DocumentFormatTest {

    @Test
    fun `DocumentFormat HTML a backend html5 et extension html`() {
        assertEquals("html5", DocumentFormat.HTML.backend)
        assertEquals("html", DocumentFormat.HTML.extension)
    }

    @Test
    fun `DocumentFormat PDF a backend pdf et extension pdf`() {
        assertEquals("pdf", DocumentFormat.PDF.backend)
        assertEquals("pdf", DocumentFormat.PDF.extension)
    }

    @Test
    fun `DocumentFormat ALL contient les 5 formats`() {
        assertEquals(5, DocumentFormat.ALL.size)
        assertTrue(DocumentFormat.ALL.contains(DocumentFormat.HTML))
        assertTrue(DocumentFormat.ALL.contains(DocumentFormat.PDF))
        assertTrue(DocumentFormat.ALL.contains(DocumentFormat.EPUB))
        assertTrue(DocumentFormat.ALL.contains(DocumentFormat.DOCBOOK))
        assertTrue(DocumentFormat.ALL.contains(DocumentFormat.MANPAGE))
    }
}

class DocumentConfigTest {

    private fun newSource(dir: File, name: String = "doc.adoc", content: String = "= Doc"): DocumentSource {
        return DocumentSource(File(dir, name).apply { writeText(content) })
    }

    private fun tempDir(): File = Files.createTempDirectory("doc-config").toFile()

    @Test
    fun `DocumentConfig outputFor genere le bon nom de fichier`() {
        val dir = tempDir()
        val source = newSource(dir, "formation.adoc", "= Formation")
        val config = DocumentConfig(source, dir)

        assertEquals(File(dir, "formation.html"), config.outputFor(DocumentFormat.HTML))
        assertEquals(File(dir, "formation.pdf"), config.outputFor(DocumentFormat.PDF))
        assertEquals(File(dir, "formation.epub"), config.outputFor(DocumentFormat.EPUB))
    }

    @Test
    fun `DocumentConfig par defaut demande HTML uniquement`() {
        val dir = tempDir()
        val source = newSource(dir)
        val config = DocumentConfig(source, dir)

        assertEquals(listOf(DocumentFormat.HTML), config.formats)
    }

    @Test
    fun `DocumentConfig requiresEnrichment faux par defaut`() {
        val dir = tempDir()
        val source = newSource(dir)
        val config = DocumentConfig(source, dir)

        assertTrue(!config.requiresEnrichment())
    }

    @Test
    fun `DocumentConfig requiresEnrichment vrai si PlantUML active`() {
        val dir = tempDir()
        val source = newSource(dir)
        val config = DocumentConfig(source, dir, enrichPlantUml = true)

        assertTrue(config.requiresEnrichment())
    }
}