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

    // --- DOC-5 : convertToEpub ---

    @Test
    fun `convertToEpub ecrit un fichier EPUB3 valide dans le fichier de sortie`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val output = File(dir, "output.epub")

        DocumentConverter.convertToEpub(source, output)

        assertTrue(output.exists(), "le fichier EPUB doit etre cree")
        assertTrue(output.length() > 100, "l'EPUB ne doit pas etre vide")
        val bytes = output.readBytes()
        // EPUB est un zip — signature PK\x03\x04
        val header = String(bytes.copyOfRange(0, minOf(4, bytes.size)))
        assertEquals("PK", header.take(2), "le fichier doit commencer par la signature zip PK")
    }

    @Test
    fun `shouldSkipEpub retourne vrai si l'EPUB et le fichier hash sidecar existent et correspondent`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val epubOutput = File(dir, "output.epub").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }
        File(dir, "output.epub.sourcehash").apply {
            writeText(DocumentConverter.sourceHash(source))
        }

        assertTrue(DocumentConverter.shouldSkipBinaryConversion(source, epubOutput))
    }

    @Test
    fun `shouldSkipEpub retourne faux si le fichier hash sidecar n existe pas`() {
        val dir = tempDir()
        val source = adocSource(dir)
        val epubOutput = File(dir, "output.epub").apply { writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04)) }

        assertFalse(DocumentConverter.shouldSkipBinaryConversion(source, epubOutput))
    }

    // --- DOC-9b : image:: directives embedding in EPUB ---

    @Test
    fun `convertToEpub embeds image referenced by image directive as a zip entry`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document avec Image

            == Illustration

            image::photo.png[Photo descriptive]

            Paragraphe suivant.
            """.trimIndent()
        )
        File(dir, "photo.png").writeBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47))
        val output = File(dir, "output.epub")

        DocumentConverter.convertToEpub(source, output)

        assertTrue(output.exists(), "le fichier EPUB doit etre cree")
        assertTrue(output.length() > 100, "l'EPUB ne doit pas etre vide")
        val entries = java.util.zip.ZipFile(output).use { zf ->
            zf.entries().toList().map { it.name }
        }
        assertTrue(
            entries.any { it.contains("photo.png") },
            "l'EPUB doit embarquer l'image referencee par image:: comme entree du zip (entries: $entries)"
        )
    }

    // --- DOC-5 : convertToDocBook ---

    @Test
    fun `convertToDocBook produit un DocBook 5 valide avec namespace XML depuis un AsciiDoc valide`() {
        val dir = tempDir()
        val source = adocSource(dir)

        val docbook = DocumentConverter.convertToDocBook(source)

        assertNotNull(docbook)
        assertTrue(docbook.contains("xmlns", ignoreCase = true), "le DocBook doit contenir un namespace XML")
        assertTrue(
            docbook.contains("<book", ignoreCase = true) || docbook.contains("<article", ignoreCase = true),
            "le DocBook doit contenir une racine <book> ou <article>"
        )
    }

    @Test
    fun `convertToDocBook preserve les sections du document source`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = "= Livre de Formation\n\n== Chapitre 1\n\nContenu du chapitre.\n\n== Chapitre 2\n\nAutre contenu."
        )

        val docbook = DocumentConverter.convertToDocBook(source)

        assertTrue(docbook.contains("Chapitre 1"), "le DocBook doit contenir la section Chapitre 1")
        assertTrue(docbook.contains("Chapitre 2"), "le DocBook doit contenir la section Chapitre 2")
    }

    // --- DOC-5 : convertToManPage ---

    @Test
    fun `convertToManPage produit une page de manuel troff valide depuis un AsciiDoc manpage`() {
        val dir = tempDir()
        val source = DocumentSource(File(dir, "man.adoc").apply {
            writeText(
                """
                = document(1)
                :doctype: manpage

                == NAME

                document - Gradle plugin for AsciiDoc publication

                == SYNOPSIS

                *document* ['OPTION']...

                == DESCRIPTION

                The *document* plugin converts AsciiDoc to multiple formats.
                """.trimIndent()
            )
        })

        val manpage = DocumentConverter.convertToManPage(source)

        assertNotNull(manpage)
        // Format troff — commence par .TH ou contient .SH
        assertTrue(
            manpage.contains(".TH") || manpage.contains(".SH") || manpage.contains(".ds"),
            "le manpage doit contenir des directives troff (.TH, .SH ou .ds)"
        )
    }

    @Test
    fun `convertToManPage leve une exception si la source est absente`() {
        val dir = tempDir()
        val source = DocumentSource(File(dir, "absent.adoc"))

        org.junit.jupiter.api.assertThrows<Exception> {
            DocumentConverter.convertToManPage(source)
        }
    }

    // --- US-DOC-06/07 (P3) : DocBook advanced rendering ---

    @Test
    fun `convertToDocBook rend les tableaux AsciiDoc en elements DocBook table`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document avec Tableau

            == Donnees

            |===
            | Colonne A | Colonne B

            | Ligne 1A | Ligne 1B
            | Ligne 2A | Ligne 2B
            |===
            """.trimIndent()
        )

        val docbook = DocumentConverter.convertToDocBook(source)

        assertNotNull(docbook)
        assertTrue(
            docbook.contains("<table", ignoreCase = true) || docbook.contains("<informaltable", ignoreCase = true),
            "le DocBook doit contenir un element <table> ou <informaltable> pour le tableau AsciiDoc"
        )
    }

    @Test
    fun `convertToDocBook rend les blocs de code AsciiDoc en elements programlisting`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document avec Code

            == Exemple

            [source,kotlin]
            ----
            fun main() {
                println("hello")
            }
            ----
            """.trimIndent()
        )

        val docbook = DocumentConverter.convertToDocBook(source)

        assertNotNull(docbook)
        assertTrue(
            docbook.contains("<programlisting", ignoreCase = true),
            "le DocBook doit contenir un element <programlisting> pour le bloc de code source"
        )
        assertTrue(docbook.contains("fun main"), "le DocBook doit preserver le contenu du bloc de code")
    }

    @Test
    fun `convertToDocBook rend les listes AsciiDoc en elements itemizedlist ou orderedlist`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document avec Listes

            == Listes

            Liste non ordonnee:

            * Element un
            * Element deux
            * Element trois

            Liste ordonnee:

            . Premier
            . Deuxieme
            . Troisieme
            """.trimIndent()
        )

        val docbook = DocumentConverter.convertToDocBook(source)

        assertNotNull(docbook)
        assertTrue(
            docbook.contains("<itemizedlist", ignoreCase = true),
            "le DocBook doit contenir un element <itemizedlist> pour la liste non ordonnee"
        )
        assertTrue(
            docbook.contains("<orderedlist", ignoreCase = true),
            "le DocBook doit contenir un element <orderedlist> pour la liste ordonnee"
        )
        assertTrue(docbook.contains("Element un"), "le DocBook doit preserver le contenu des items de liste")
        assertTrue(docbook.contains("Premier"), "le DocBook doit preserver le contenu des items ordonnes")
    }

    // --- US-DOC-06/07 (P3) : ManPage advanced rendering ---

    @Test
    fun `convertToManPage rend la section SYNOPSIS avec les options en gras troff`() {
        val dir = tempDir()
        val source = DocumentSource(File(dir, "man.adoc").apply {
            writeText(
                """
                = document(1)
                :doctype: manpage

                == NAME

                document - Gradle plugin for AsciiDoc publication

                == SYNOPSIS

                *document* ['OPTION']...

                == OPTIONS

                *--html*::
                    Generate HTML output.
                *--pdf*::
                    Generate PDF output.
                """.trimIndent()
            )
        })

        val manpage = DocumentConverter.convertToManPage(source)

        assertNotNull(manpage)
        assertTrue(manpage.contains("SYNOPSIS"), "le manpage doit contenir la section SYNOPSIS")
        assertTrue(manpage.contains("OPTIONS") || manpage.contains("options"), "le manpage doit contenir la section OPTIONS")
        assertTrue(
            manpage.contains(".B ") || manpage.contains("\\fB"),
            "le manpage doit contenir une directive de gras troff (.B ou \\fB) pour les options formatees"
        )
    }

    @Test
    fun `convertToManPage rend les sections imbriquees du document source`() {
        val dir = tempDir()
        val source = DocumentSource(File(dir, "man.adoc").apply {
            writeText(
                """
                = document(1)
                :doctype: manpage

                == NAME

                document - Gradle plugin for AsciiDoc publication

                == DESCRIPTION

                The *document* plugin converts AsciiDoc to multiple formats.

                === Sub-section

                Detailed description of a sub-feature.

                == EXAMPLES

                *document* --html livre.adoc
                """.trimIndent()
            )
        })

        val manpage = DocumentConverter.convertToManPage(source)

        assertNotNull(manpage)
        assertTrue(manpage.contains("DESCRIPTION"), "le manpage doit contenir la section DESCRIPTION")
        assertTrue(manpage.contains("EXAMPLES"), "le manpage doit contenir la section EXAMPLES")
        // AsciidoctorJ manpage renders === sub-sections as .SS (sub-section troff directive)
        // or preserves the heading text. Either signal validates the sub-section is present.
        assertTrue(
            manpage.contains("Sub-section") || manpage.contains(".SS"),
            "le manpage doit rendre les sous-sections (texte ou directive .SS): $manpage"
        )
    }
}