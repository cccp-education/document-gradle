package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

import java.io.File
import java.nio.file.Files

class DocumentArtifactCollectorTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-collector").toFile()

    @Test
    fun `collect returns empty list when output dir does not exist`() {
        val collector = DocumentArtifactCollector(outputDir = File(tempDir(), "missing"))

        val entries = collector.collect(sourceAdoc = "source.adoc")

        assertTrue(entries.isEmpty())
    }

    @Test
    fun `collect detects an HTML artifact`() {
        val dir = tempDir()
        File(dir, "document.html").writeText("<html></html>")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        assertEquals(1, entries.size)
        val entry = entries[0]
        assertEquals(DocumentFormat.HTML, entry.format)
        assertTrue(entry.exists)
        assertEquals("source.adoc", entry.sourceAdoc)
    }

    @Test
    fun `collect detects a PDF artifact`() {
        val dir = tempDir()
        File(dir, "document.pdf").writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        assertEquals(1, entries.size)
        assertEquals(DocumentFormat.PDF, entries[0].format)
    }

    @Test
    fun `collect detects all 5 format artifacts plus generated and enriched adoc`() {
        val dir = tempDir()
        File(dir, "document.adoc").writeText("= Doc")
        File(dir, "document-enriched.adoc").writeText("= Doc enriched")
        File(dir, "document.html").writeText("<html></html>")
        File(dir, "document.pdf").writeBytes(byteArrayOf(0x25, 0x50, 0x44, 0x46))
        File(dir, "document.epub").writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04))
        File(dir, "document.xml").writeText("<book/>")
        File(dir, "document.man").writeText(".TH doc")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        assertEquals(7, entries.size)
        val formats = entries.map { it.format }.toSet()
        assertTrue(formats.contains(DocumentFormat.HTML))
        assertTrue(formats.contains(DocumentFormat.PDF))
        assertTrue(formats.contains(DocumentFormat.EPUB))
        assertTrue(formats.contains(DocumentFormat.DOCBOOK))
        assertTrue(formats.contains(DocumentFormat.MANPAGE))
    }

    @Test
    fun `collect skips non-existing artifacts and only returns files on disk`() {
        val dir = tempDir()
        File(dir, "document.html").writeText("<html></html>")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        val htmlEntry = entries.find { it.format == DocumentFormat.HTML }
        assertTrue(htmlEntry?.exists == true)
        val pdfEntry = entries.find { it.format == DocumentFormat.PDF }
        assertTrue(pdfEntry == null, "non-existing PDF should not be in entries")
    }

    @Test
    fun `collect includes the generated adoc as an entry`() {
        val dir = tempDir()
        File(dir, "document.adoc").writeText("= Generated Doc")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        val adocEntry = entries.find { it.path.endsWith("document.adoc") }
        assertTrue(adocEntry != null)
        assertTrue(adocEntry!!.exists)
    }

    @Test
    fun `collect includes the enriched adoc as an entry`() {
        val dir = tempDir()
        File(dir, "document-enriched.adoc").writeText("= Enriched Doc")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val entries = collector.collect(sourceAdoc = "source.adoc")

        val enrichedEntry = entries.find { it.path.endsWith("document-enriched.adoc") }
        assertTrue(enrichedEntry != null)
        assertTrue(enrichedEntry!!.exists)
    }

    // --- DOC-8.3 — release notes scanning ---

    @Test
    fun `collectReleaseNotes returns empty list when release-notes dir does not exist`() {
        val dir = tempDir()
        val collector = DocumentArtifactCollector(outputDir = dir)

        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = File(dir, "release-notes"))

        assertTrue(releaseNotes.isEmpty())
    }

    @Test
    fun `collectReleaseNotes detects an adoc release-notes file`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.adoc").writeText("= Release Notes")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(1, releaseNotes.size)
        val entry = releaseNotes[0]
        assertTrue(entry.exists)
        assertEquals("asciidoc", entry.rendererType)
        assertTrue(entry.path.endsWith("release-notes-1.0.0.adoc"))
    }

    @Test
    fun `collectReleaseNotes detects a markdown release-notes file`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.md").writeText("# Release Notes")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(1, releaseNotes.size)
        assertEquals("markdown", releaseNotes[0].rendererType)
    }

    @Test
    fun `collectReleaseNotes detects a json release-notes file`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.json").writeText("{\"version\":\"1.0.0\"}")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(1, releaseNotes.size)
        assertEquals("json", releaseNotes[0].rendererType)
    }

    @Test
    fun `collectReleaseNotes detects multiple release-notes files with different renderers`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.adoc").writeText("= Release Notes")
        File(releaseNotesDir, "release-notes-1.0.0.md").writeText("# Release Notes")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(2, releaseNotes.size)
        val rendererTypes = releaseNotes.map { it.rendererType }.toSet()
        assertTrue(rendererTypes.contains("asciidoc"))
        assertTrue(rendererTypes.contains("markdown"))
    }

    @Test
    fun `collectReleaseNotes skips non-release-notes files`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.adoc").writeText("= Release Notes")
        File(releaseNotesDir, "notes.txt").writeText("random")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(1, releaseNotes.size)
        assertTrue(releaseNotes[0].path.endsWith(".adoc"))
    }

    @Test
    fun `collectReleaseNotes first entry path becomes primary release-notes path`() {
        val dir = tempDir()
        val releaseNotesDir = File(dir, "release-notes").apply { mkdirs() }
        File(releaseNotesDir, "release-notes-1.0.0.adoc").writeText("= Release Notes")

        val collector = DocumentArtifactCollector(outputDir = dir)
        val releaseNotes = collector.collectReleaseNotes(releaseNotesDir = releaseNotesDir)

        assertEquals(1, releaseNotes.size)
        assertEquals(
            File(releaseNotesDir, "release-notes-1.0.0.adoc").absolutePath,
            releaseNotes[0].path,
        )
    }
}