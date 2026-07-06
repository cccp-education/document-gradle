package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.nio.file.Files

class DocumentEnricherTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-enrich").toFile()

    private fun adocSource(dir: File, name: String = "source.adoc", content: String): DocumentSource {
        return DocumentSource(File(dir, name).apply { writeText(content) })
    }

    @Test
    fun `enrich preserves inline plantuml blocks without escaping`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document with Diagram

            == Architecture

            [plantuml]
            ----
            @startuml
            Alice -> Bob: hello
            @enduml
            ----
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(enriched.contains("[plantuml]"), "the [plantuml] block must be preserved")
        assertTrue(enriched.contains("@startuml"), "the plantuml content must be preserved")
        assertTrue(enriched.contains("@enduml"), "the plantuml content must be preserved")
    }

    @Test
    fun `enrich preserves raw HTML passthrough blocks without escaping`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document with raw HTML

            ++++
            <iframe src="https://example.com" width="600" height="400"></iframe>
            ++++

            Next paragraph.
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(enriched.contains("++++"), "the passthrough delimiters must be preserved")
        assertTrue(enriched.contains("<iframe"), "the raw HTML must be preserved without escaping")
    }

    @Test
    fun `enrich recursively resolves AsciiDoc includes`() {
        val dir = tempDir()
        File(dir, "chapter.adoc").writeText("== Included Chapter\n\nText of the included chapter.")
        val source = adocSource(
            dir,
            content = """
            = Document with Include

            include::chapter.adoc[]
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(enriched.contains("Included Chapter"), "the included file content must be resolved")
        assertTrue(!enriched.contains("include::chapter.adoc[]"), "the include directive must be replaced by its content")
    }

    @Test
    fun `enrich preserves the title and sections of the source document`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Training Book

            == Chapter 1

            Chapter content.

            == Chapter 2

            Other content.
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(enriched.contains("= Training Book"), "the title must be preserved")
        assertTrue(enriched.contains("Chapter 1"), "the sections must be preserved")
        assertTrue(enriched.contains("Chapter 2"), "the sections must be preserved")
    }

    @Test
    fun `enrich does not mutate the source file`() {
        val dir = tempDir()
        val sourceFile = File(dir, "source.adoc").apply {
            writeText("= Source Document\n\n== Section\n\nContent.")
        }
        val source = DocumentSource(sourceFile)
        val originalContent = sourceFile.readText()

        DocumentEnricher.enrich(source)

        assertEquals(originalContent, sourceFile.readText(), "the source file must not be modified")
    }

    @Test
    fun `enrich preserves image directives without escaping`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document with Image

            == Illustration

            image::photo.png[]

            Paragraph after the image.
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(enriched.contains("image::photo.png[]"), "the image directive must be preserved verbatim")
        assertTrue(!enriched.contains("image\\::"), "the image directive must not be escaped")
    }

    @Test
    fun `enrich preserves inline image syntax with alt text`() {
        val dir = tempDir()
        val source = adocSource(
            dir,
            content = """
            = Document with Captioned Image

            image::diagram.png[Architecture diagram]

            Next paragraph.
            """.trimIndent()
        )

        val enriched = DocumentEnricher.enrich(source)

        assertTrue(
            enriched.contains("image::diagram.png[Architecture diagram]"),
            "the inline image with alt text must be preserved verbatim",
        )
    }
}