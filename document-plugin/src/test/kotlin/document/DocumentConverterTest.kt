package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import java.io.File
import java.nio.file.Files

class DocumentConverterTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-conv").toFile()

    private fun adocSource(dir: File, name: String = "source.adoc", content: String = "= Document de Test\n\n== Introduction\n\nParagraphe."): DocumentSource {
        return DocumentSource(File(dir, name).apply { writeText(content) })
    }

    @Test
    fun `convertToHtml produit un HTML5 avec doctype depuis un AsciiDoc valide`() {
        val dir = tempDir()
        val source = adocSource(dir)

        val html = DocumentConverter.convertToHtml(source)

        assertNotNull(html)
        assertTrue(html.contains("<!DOCTYPE html", ignoreCase = true), "le HTML doit contenir une declaration doctype")
        assertTrue(html.contains("Document de Test"), "le HTML doit contenir le titre du document source")
    }

    @Test
    fun `convertToHtml preserve les sections du document source`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = "= Livre de Formation\n\n== Chapitre 1\n\nContenu du chapitre.\n\n== Chapitre 2\n\nAutre contenu."
        )

        val html = DocumentConverter.convertToHtml(source)

        assertTrue(html.contains("Chapitre 1"), "le HTML doit contenir la section Chapitre 1")
        assertTrue(html.contains("Chapitre 2"), "le HTML doit contenir la section Chapitre 2")
    }

    @Test
    fun `convertToHtml rend les blocs de code source avec syntaxe colorisee`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
                = Document Code

                [source,kotlin]
                ----
                fun hello() = println("hi")
                ----
            """.trimIndent()
        )

        val html = DocumentConverter.convertToHtml(source)

        assertTrue(html.contains("fun hello"), "le HTML doit contenir le code source")
        assertTrue(html.contains("highlight") || html.contains("<code"), "le HTML doit contenir un bloc de code")
    }

    @Test
    fun `convertToHtml resout les includes AsciiDoc`() {
        val dir = tempDir()
        File(dir, "chapitre.adoc").writeText("== Chapitre Inclu\n\nTexte du chapitre inclus.")
        val source = adocSource(
            dir,
            content = "= Document avec Include\n\ninclude::chapitre.adoc[]"
        )

        val html = DocumentConverter.convertToHtml(source)

        assertTrue(html.contains("Chapitre Inclu"), "le HTML doit contenir le contenu du fichier inclus")
    }

    @Test
    fun `convertToHtml leve une exception si la source est absente`() {
        val dir = tempDir()
        val source = DocumentSource(File(dir, "absent.adoc")) // fichier .adoc valide mais inexistant

        org.junit.jupiter.api.assertThrows<Exception> {
            DocumentConverter.convertToHtml(source)
        }
    }

    @Test
    fun `shouldSkip retourne vrai si le fichier existe et le hash source correspond`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val output = File(dir, "output.html").apply {
            writeText("<!DOCTYPE html><html><body>existing</body></html>")
            // metadata: hash source inscrit dans un header
            writeText("<!-- document-gradle — source hash: ${DocumentConverter.sourceHash(source)} -->\n<!DOCTYPE html>\n<html></html>")
        }

        assertTrue(DocumentConverter.shouldSkipConversion(source, output))
    }

    @Test
    fun `shouldSkip retourne faux si le fichier n existe pas`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val output = File(dir, "absent.html")

        assertFalse(DocumentConverter.shouldSkipConversion(source, output))
    }

    @Test
    fun `shouldSkip retourne faux si le hash source ne correspond pas`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val output = File(dir, "output.html").apply {
            writeText("<!-- document-gradle — source hash: differethash -->\n<!DOCTYPE html>")
        }

        assertFalse(DocumentConverter.shouldSkipConversion(source, output))
    }

    @Test
    fun `buildMetadataHeader inscrit le hash de la source`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val header = DocumentConverter.buildMetadataHeader(source)

        assertTrue(header.contains("source hash:"))
        assertTrue(header.contains(DocumentConverter.sourceHash(source)))
    }

    // --- DOC-4 : convertToPdf ---

    @Test
    fun `convertToPdf ecrit un fichier PDF valide dans le fichier de sortie`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val output = File(dir, "output.pdf")

        DocumentConverter.convertToPdf(source, output)

        assertTrue(output.exists(), "le fichier PDF doit etre cree")
        assertTrue(output.length() > 100, "le PDF ne doit pas etre vide")
        val header = String(output.readBytes().copyOfRange(0, minOf(5, output.length().toInt())))
        assertEquals("%PDF-", header, "le fichier doit commencer par la signature PDF")
    }

    @Test
    fun `convertToPdf preserve les sections du document source`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Livre de Formation

            == Chapitre 1

            Contenu du chapitre.

            == Chapitre 2

            Autre contenu.
            """.trimIndent()
        )
        val output = File(dir, "livre.pdf")

        DocumentConverter.convertToPdf(source, output)

        assertTrue(output.exists())
        assertTrue(output.length() > 500, "le PDF doit contenir le contenu des sections")
    }

    @Test
    fun `shouldSkipPdf retourne vrai si le PDF et le fichier hash sidecar existent et correspondent`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val pdfOutput = File(dir, "output.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) }
        val hashFile = File(dir, "output.pdf.sourcehash").apply {
            writeText(DocumentConverter.sourceHash(source))
        }

        assertTrue(DocumentConverter.shouldSkipBinaryConversion(source, pdfOutput))
    }

    @Test
    fun `shouldSkipPdf retourne faux si le fichier hash sidecar n existe pas`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val pdfOutput = File(dir, "output.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) }
        // pas de fichier .sourcehash

        assertFalse(DocumentConverter.shouldSkipBinaryConversion(source, pdfOutput))
    }

    @Test
    fun `shouldSkipPdf retourne faux si le hash dans le sidecar ne correspond pas`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val pdfOutput = File(dir, "output.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) }
        File(dir, "output.pdf.sourcehash").apply { writeText("differethash") }

        assertFalse(DocumentConverter.shouldSkipBinaryConversion(source, pdfOutput))
    }

    @Test
    fun `writeBinaryMetadataHeader ecrit le hash de la source dans un fichier sidecar`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val pdfOutput = File(dir, "output.pdf").apply { writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46)) }

        DocumentConverter.writeBinaryMetadataHeader(source, pdfOutput)
        val sidecar = File(dir, "output.pdf.sourcehash")

        assertTrue(sidecar.exists(), "le fichier sidecar .sourcehash doit etre cree")
        assertEquals(DocumentConverter.sourceHash(source), sidecar.readText().trim())
    }
}