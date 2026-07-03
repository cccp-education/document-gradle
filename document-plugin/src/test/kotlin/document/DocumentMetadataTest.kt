package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class DocumentMetadataTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-meta").toFile()

    @Test
    fun `DocumentMetadata forNewOrleans sets source to new-orleans`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertEquals("new-orleans", metadata.source)
    }

    @Test
    fun `DocumentMetadata forNewOrleans defaults type to retrieve`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertEquals("retrieve", metadata.type)
    }

    @Test
    fun `DocumentMetadata forNewOrleans defaults model to onnx-local`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertEquals("onnx-local", metadata.model)
    }

    @Test
    fun `DocumentMetadata forNewOrleans defaults version to 1_0`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertEquals("1.0", metadata.version)
    }

    @Test
    fun `DocumentMetadata forNewOrleans defaults dependencies to brooklyn and htown`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertTrue(metadata.dependencies.contains("brooklyn"))
        assertTrue(metadata.dependencies.contains("htown"))
    }

    @Test
    fun `DocumentMetadata forNewOrleans accepts custom sessions count`() {
        val metadata = DocumentMetadata.forNewOrleans(sessions = 3)

        assertEquals(3, metadata.sessions)
    }

    @Test
    fun `DocumentMetadata forNewOrleans accepts custom type`() {
        val metadata = DocumentMetadata.forNewOrleans(type = "composite-context")

        assertEquals("composite-context", metadata.type)
    }

    @Test
    fun `DocumentMetadata forNewOrleans generates an ISO-8601 timestamp`() {
        val metadata = DocumentMetadata.forNewOrleans()

        assertNotNull(metadata.generatedAt)
        assertTrue(metadata.generatedAt.contains("T"))
        assertTrue(metadata.generatedAt.endsWith("Z"))
    }

    @Test
    fun `DocumentMetadata writeTo creates a metadata_json file`() {
        val dir = tempDir()
        val metadata = DocumentMetadata.forNewOrleans(sessions = 2)

        val file = DocumentMetadata.writeTo(dir, metadata)

        assertEquals(File(dir, "metadata.json"), file)
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"source\""))
        assertTrue(content.contains("\"new-orleans\""))
        assertTrue(content.contains("\"sessions\" : 2"))
    }

    @Test
    fun `DocumentMetadata writeTo creates parent directories if missing`() {
        val dir = tempDir()
        val nested = File(dir, "build/docs/document")

        val metadata = DocumentMetadata.forNewOrleans()
        DocumentMetadata.writeTo(nested, metadata)

        assertTrue(File(nested, "metadata.json").exists())
    }
}