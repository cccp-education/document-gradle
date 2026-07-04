package document

import org.junit.jupiter.api.Assertions.assertEquals
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
}