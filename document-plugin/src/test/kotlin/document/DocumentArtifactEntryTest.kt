package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.io.File
import java.nio.file.Files

class DocumentArtifactEntryTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-entry").toFile()

    @Test
    fun `DocumentArtifactEntry stores path format and source adoc`() {
        val file = File(tempDir(), "document.html")
        file.writeText("<html></html>")
        val entry = DocumentArtifactEntry(
            path = file.absolutePath,
            format = DocumentFormat.HTML,
            sourceAdoc = "source.adoc",
            exists = true
        )

        assertEquals(file.absolutePath, entry.path)
        assertEquals(DocumentFormat.HTML, entry.format)
        assertEquals("source.adoc", entry.sourceAdoc)
        assertTrue(entry.exists)
    }

    @Test
    fun `DocumentArtifactEntry toMap produces N3-compatible entry`() {
        val entry = DocumentArtifactEntry(
            path = "/build/docs/document/document.html",
            format = DocumentFormat.HTML,
            sourceAdoc = "source.adoc",
            exists = true
        )

        val map = entry.toMap()

        assertEquals("document", map["source"])
        assertEquals("/build/docs/document/document.html", map["path"])
        assertEquals("html", map["format"])
        assertEquals("html5", map["backend"])
        assertEquals("source.adoc", map["sourceAdoc"])
        assertEquals(true, map["exists"])
    }

    @Test
    fun `DocumentArtifactEntry toMap for PDF format produces correct fields`() {
        val entry = DocumentArtifactEntry(
            path = "/build/docs/document/document.pdf",
            format = DocumentFormat.PDF,
            sourceAdoc = "livre.adoc",
            exists = true
        )

        val map = entry.toMap()

        assertEquals("pdf", map["format"])
        assertEquals("pdf", map["backend"])
    }

    @Test
    fun `DocumentArtifactEntry marks non-existing file as exists false`() {
        val entry = DocumentArtifactEntry(
            path = "/build/docs/document/missing.html",
            format = DocumentFormat.HTML,
            sourceAdoc = "source.adoc",
            exists = false
        )

        assertEquals(false, entry.toMap()["exists"])
    }
}

class DocumentRetrieveResultTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-retrieve").toFile()

    @Test
    fun `DocumentRetrieveResult defaults source to new-orleans`() {
        val result = DocumentRetrieveResult(entries = emptyList())

        assertEquals("new-orleans", result.source)
    }

    @Test
    fun `DocumentRetrieveResult count matches entries size`() {
        val entries = listOf(
            DocumentArtifactEntry("/p1.html", DocumentFormat.HTML, "s.adoc", true),
            DocumentArtifactEntry("/p2.pdf", DocumentFormat.PDF, "s.adoc", true)
        )
        val result = DocumentRetrieveResult(entries = entries)

        assertEquals(2, result.count)
    }

    @Test
    fun `DocumentRetrieveResult count is zero for empty entries`() {
        val result = DocumentRetrieveResult(entries = emptyList())

        assertEquals(0, result.count)
    }

    @Test
    fun `DocumentRetrieveResult toMap produces N3-compatible envelope`() {
        val entries = listOf(
            DocumentArtifactEntry("/p.html", DocumentFormat.HTML, "s.adoc", true)
        )
        val result = DocumentRetrieveResult(entries = entries)

        val map = result.toMap()

        assertEquals("new-orleans", map["source"])
        assertEquals(1, map["count"])
        @Suppress("UNCHECKED_CAST")
        val mapEntries = map["entries"] as List<Map<String, Any>>
        assertEquals(1, mapEntries.size)
        assertEquals("/p.html", mapEntries[0]["path"])
    }

    @Test
    fun `DocumentRetrieveResult toMap includes timestamp`() {
        val result = DocumentRetrieveResult(entries = emptyList())

        val map = result.toMap()

        assertTrue(map.containsKey("timestamp"))
        assertTrue(map["timestamp"] is Long)
    }

    @Test
    fun `DocumentRetrieveResult writeTo creates composite-context json`() {
        val dir = tempDir()
        val result = DocumentRetrieveResult(entries = emptyList())

        val file = result.writeTo(dir)

        assertEquals(File(dir, "composite-context.json"), file)
        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"source\""))
        assertTrue(content.contains("\"new-orleans\""))
        assertTrue(content.contains("\"entries\""))
        assertTrue(content.contains("\"count\""))
    }

    @Test
    fun `DocumentRetrieveResult writeTo creates parent directories if missing`() {
        val dir = tempDir()
        val nested = File(dir, "build/docs/document")

        val result = DocumentRetrieveResult(entries = emptyList())
        result.writeTo(nested)

        assertTrue(File(nested, "composite-context.json").exists())
    }

    // --- DOC-8.3 — release notes N3 integration ---

    @Test
    fun `DocumentRetrieveResult defaults releaseNotes to empty list`() {
        val result = DocumentRetrieveResult(entries = emptyList())

        assertTrue(result.releaseNotes.isEmpty())
    }

    @Test
    fun `DocumentRetrieveResult releaseNotesCount matches releaseNotes size`() {
        val releaseNotes = listOf(
            ReleaseNotesArtifactEntry("/rn.adoc", "asciidoc", true),
            ReleaseNotesArtifactEntry("/rn.md", "markdown", true),
        )
        val result = DocumentRetrieveResult(entries = emptyList(), releaseNotes = releaseNotes)

        assertEquals(2, result.releaseNotesCount)
    }

    @Test
    fun `DocumentRetrieveResult releaseNotesCount is zero when no release notes`() {
        val result = DocumentRetrieveResult(entries = emptyList())

        assertEquals(0, result.releaseNotesCount)
    }

    @Test
    fun `DocumentRetrieveResult toMap includes releaseNotes array`() {
        val releaseNotes = listOf(
            ReleaseNotesArtifactEntry("/build/release-notes/rn.adoc", "asciidoc", true),
        )
        val result = DocumentRetrieveResult(entries = emptyList(), releaseNotes = releaseNotes)

        val map = result.toMap()

        assertTrue(map.containsKey("releaseNotes"))
        @Suppress("UNCHECKED_CAST")
        val rnEntries = map["releaseNotes"] as List<Map<String, Any>>
        assertEquals(1, rnEntries.size)
        assertEquals("/build/release-notes/rn.adoc", rnEntries[0]["path"])
        assertEquals("release-notes", rnEntries[0]["type"])
        assertEquals("asciidoc", rnEntries[0]["rendererType"])
    }

    @Test
    fun `DocumentRetrieveResult toMap includes releaseNotesCount`() {
        val result = DocumentRetrieveResult(
            entries = emptyList(),
            releaseNotes = listOf(ReleaseNotesArtifactEntry("/rn.adoc", "asciidoc", true)),
        )

        val map = result.toMap()

        assertEquals(1, map["releaseNotesCount"])
    }

    @Test
    fun `DocumentRetrieveResult writeTo creates composite-context json with release notes`() {
        val dir = tempDir()
        val releaseNotes = listOf(
            ReleaseNotesArtifactEntry("/build/release-notes/rn.adoc", "asciidoc", true),
        )
        val result = DocumentRetrieveResult(entries = emptyList(), releaseNotes = releaseNotes)

        val file = result.writeTo(dir)

        assertTrue(file.exists())
        val content = file.readText()
        assertTrue(content.contains("\"releaseNotes\""))
        assertTrue(content.contains("\"releaseNotesCount\""))
        assertTrue(content.contains("/build/release-notes/rn.adoc"))
        assertTrue(content.contains("\"rendererType\""))
    }
}