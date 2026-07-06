package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class DocumentConfigSerializerTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-cfg-ser").toFile()

    @Test
    fun `serialize writes a json file with source enrich outputs and metadata`() {
        val dir = tempDir()
        val sourceFile = File(dir, "livre.adoc").apply { writeText("= Livre") }
        val config = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            enrich = DocumentEnrichConfig(plantuml = true, images = true, passthrough = false),
            outputs = DocumentOutputs(html = true, pdf = true, epub = false),
            frontMatter = DocumentFrontMatter(title = "Mon Livre", author = "Auteur", language = "fr"),
        )
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)

        assertTrue(file.exists())
        val json = file.readText()
        assertTrue(json.contains("\"source\""))
        assertTrue(json.contains("livre.adoc"))
        assertTrue(json.contains("\"enrich\""))
        assertTrue(json.contains("\"plantuml\" : true"))
        assertTrue(json.contains("\"images\" : true"))
        assertTrue(json.contains("\"outputs\""))
        assertTrue(json.contains("\"pdf\" : true"))
        assertTrue(json.contains("\"metadata\""))
        assertTrue(json.contains("Mon Livre"))
        assertTrue(json.contains("\"language\" : \"fr\""))
    }

    @Test
    fun `serialize uses defaults when config fields are default`() {
        val dir = tempDir()
        val sourceFile = File(dir, "doc.adoc").apply { writeText("= Doc") }
        val config = DocumentPipelineConfig(source = DocumentSource(sourceFile))
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)

        assertTrue(file.exists())
        val json = file.readText()
        // Default enrich flags all false
        assertTrue(json.contains("\"plantuml\" : false"))
        assertTrue(json.contains("\"images\" : false"))
        assertTrue(json.contains("\"passthrough\" : false"))
        // Default outputs html only
        assertTrue(json.contains("\"html\" : true"))
        assertTrue(json.contains("\"pdf\" : false"))
        // Default metadata language fr
        assertTrue(json.contains("\"language\" : \"fr\""))
    }

    @Test
    fun `serialize produces valid json with expected top-level keys`() {
        val dir = tempDir()
        val sourceFile = File(dir, "x.adoc").apply { writeText("= X") }
        val config = DocumentPipelineConfig(source = DocumentSource(sourceFile))
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)
        val json = file.readText()

        assertTrue(json.contains("\"source\""))
        assertTrue(json.contains("\"enrich\""))
        assertTrue(json.contains("\"outputs\""))
        assertTrue(json.contains("\"theme\""))
        assertTrue(json.contains("\"metadata\""))
    }

    @Test
    fun `serialize includes theme block when theme is provided`() {
        val dir = tempDir()
        val sourceFile = File(dir, "x.adoc").apply { writeText("= X") }
        val css = File(dir, "talaria.css").apply { writeText("body {}") }
        val theme = DocumentTheme(htmlStylesheet = css)
        val config = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            theme = theme,
        )
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)
        val json = file.readText()

        assertTrue(json.contains("\"theme\""))
        assertTrue(json.contains("talaria.css"))
    }

    // --- DOC-12 extension : book { } serialisation ---

    @Test
    fun `serialize includes book block when book is provided`() {
        val dir = tempDir()
        val sourceFile = File(dir, "livre.adoc").apply { writeText("= Livre") }
        val pagesDir = File(dir, "pages").apply { mkdirs() }
        val photosDir = File(dir, "photos").apply { mkdirs() }
        val config = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            book = BookConfig(
                pagesDir = pagesDir,
                photosDir = photosDir,
                title = "Mon Livre",
                author = "Auteur",
            ),
        )
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)
        val json = file.readText()

        assertTrue(json.contains("\"book\""), "the JSON must contain a book block (json=$json)")
        assertTrue(json.contains("Mon Livre"), "the book block must contain the title")
        assertTrue(json.contains("Auteur"), "the book block must contain the author")
        assertTrue(json.contains("pages"), "the book block must contain the pagesDir")
        assertTrue(json.contains("photos"), "the book block must contain the photosDir")
    }

    @Test
    fun `serialize omits book block when book is not provided`() {
        val dir = tempDir()
        val sourceFile = File(dir, "x.adoc").apply { writeText("= X") }
        val config = DocumentPipelineConfig(source = DocumentSource(sourceFile))
        val serializer = DocumentConfigSerializer()

        val file = serializer.serialize(dir, config)
        val json = file.readText()

        assertTrue(!json.contains("\"book\""), "the JSON must not contain a book block when book is unset (json=$json)")
    }

    // --- DOC-12 round-trip : deserialize (reverse of serialize) ---

    @Test
    fun `deserialize reads back a serialized config preserving source enrich outputs and metadata`() {
        val dir = tempDir()
        val sourceFile = File(dir, "livre.adoc").apply { writeText("= Livre") }
        val original = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            enrich = DocumentEnrichConfig(plantuml = true, images = true, passthrough = false),
            outputs = DocumentOutputs(html = true, pdf = true, epub = false),
            frontMatter = DocumentFrontMatter(title = "Mon Livre", author = "Auteur", language = "fr"),
        )
        val serializer = DocumentConfigSerializer()
        val file = serializer.serialize(dir, original)

        val roundTripped = serializer.deserialize(file, baseDir = dir)

        assertEquals("livre.adoc", roundTripped.source.file.name)
        assertEquals(DocumentEnrichConfig(plantuml = true, images = true, passthrough = false), roundTripped.enrich)
        assertEquals(DocumentOutputs(html = true, pdf = true, epub = false), roundTripped.outputs)
        assertEquals("Mon Livre", roundTripped.frontMatter.title)
        assertEquals("Auteur", roundTripped.frontMatter.author)
        assertEquals("fr", roundTripped.frontMatter.language)
    }

    @Test
    fun `deserialize reads back a serialized config with defaults when no block is set`() {
        val dir = tempDir()
        val sourceFile = File(dir, "doc.adoc").apply { writeText("= Doc") }
        val original = DocumentPipelineConfig(source = DocumentSource(sourceFile))
        val serializer = DocumentConfigSerializer()
        val file = serializer.serialize(dir, original)

        val roundTripped = serializer.deserialize(file, baseDir = dir)

        assertEquals("doc.adoc", roundTripped.source.file.name)
        assertEquals(DocumentEnrichConfig(), roundTripped.enrich)
        assertEquals(DocumentOutputs(), roundTripped.outputs)
        assertEquals(DocumentFrontMatter(), roundTripped.frontMatter)
    }

    @Test
    fun `deserialize reads back a serialized config with theme block when theme is provided`() {
        val dir = tempDir()
        val sourceFile = File(dir, "x.adoc").apply { writeText("= X") }
        val css = File(dir, "talaria.css").apply { writeText("body {}") }
        val original = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            theme = DocumentTheme(htmlStylesheet = css),
        )
        val serializer = DocumentConfigSerializer()
        val file = serializer.serialize(dir, original)

        val roundTripped = serializer.deserialize(file, baseDir = dir)

        assertEquals("talaria.css", roundTripped.theme.htmlStylesheet?.name)
    }

    @Test
    fun `deserialize reads back a serialized config with book block when book is provided`() {
        val dir = tempDir()
        val sourceFile = File(dir, "livre.adoc").apply { writeText("= Livre") }
        val pagesDir = File(dir, "pages").apply { mkdirs() }
        val photosDir = File(dir, "photos").apply { mkdirs() }
        val original = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            book = BookConfig(
                pagesDir = pagesDir,
                photosDir = photosDir,
                title = "Mon Livre",
                author = "Auteur",
            ),
        )
        val serializer = DocumentConfigSerializer()
        val file = serializer.serialize(dir, original)

        val roundTripped = serializer.deserialize(file, baseDir = dir)

        assertFalse(roundTripped.book.isEmpty(), "the book block must be present after round-trip")
        assertEquals("Mon Livre", roundTripped.book.title)
        assertEquals("Auteur", roundTripped.book.author)
        assertEquals("pages", roundTripped.book.pagesDir?.name)
        assertEquals("photos", roundTripped.book.photosDir?.name)
    }

    @Test
    fun `round-trip serialize then deserialize is idempotent`() {
        val dir = tempDir()
        val sourceFile = File(dir, "livre.adoc").apply { writeText("= Livre") }
        val original = DocumentPipelineConfig(
            source = DocumentSource(sourceFile),
            enrich = DocumentEnrichConfig(plantuml = true, images = false, passthrough = true),
            outputs = DocumentOutputs(html = true, pdf = true, epub = true, docbook = false, manpage = false),
            frontMatter = DocumentFrontMatter(title = "Round Trip", author = "RT", language = "en"),
        )
        val serializer = DocumentConfigSerializer()
        val file1 = serializer.serialize(dir, original)
        val first = serializer.deserialize(file1, baseDir = dir)
        val file2 = serializer.serialize(dir, first)
        val second = serializer.deserialize(file2, baseDir = dir)

        assertEquals(first.enrich, second.enrich)
        assertEquals(first.outputs, second.outputs)
        assertEquals(first.frontMatter, second.frontMatter)
        assertEquals(first.source.file.name, second.source.file.name)
        assertEquals(file1.readText(), file2.readText(), "the two serialisations must be byte-identical (idempotent)")
    }
}