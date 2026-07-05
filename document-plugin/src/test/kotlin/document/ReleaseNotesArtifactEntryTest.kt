package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ReleaseNotesArtifactEntryTest {

    @Test
    fun `ReleaseNotesArtifactEntry stores path rendererType and exists`() {
        val entry = ReleaseNotesArtifactEntry(
            path = "/build/release-notes/release-notes-1.0.0.adoc",
            rendererType = "asciidoc",
            exists = true,
        )

        assertEquals("/build/release-notes/release-notes-1.0.0.adoc", entry.path)
        assertEquals("asciidoc", entry.rendererType)
        assertTrue(entry.exists)
    }

    @Test
    fun `ReleaseNotesArtifactEntry toMap produces N3-compatible entry with source document`() {
        val entry = ReleaseNotesArtifactEntry(
            path = "/build/release-notes/release-notes-1.0.0.adoc",
            rendererType = "asciidoc",
            exists = true,
        )

        val map = entry.toMap()

        assertEquals("document", map["source"])
        assertEquals("/build/release-notes/release-notes-1.0.0.adoc", map["path"])
        assertEquals("release-notes", map["type"])
        assertEquals("asciidoc", map["rendererType"])
        assertEquals(true, map["exists"])
    }

    @Test
    fun `ReleaseNotesArtifactEntry toMap for markdown renderer produces correct rendererType`() {
        val entry = ReleaseNotesArtifactEntry(
            path = "/build/release-notes/release-notes-1.0.0.md",
            rendererType = "markdown",
            exists = true,
        )

        assertEquals("markdown", entry.toMap()["rendererType"])
    }

    @Test
    fun `ReleaseNotesArtifactEntry marks non-existing file as exists false`() {
        val entry = ReleaseNotesArtifactEntry(
            path = "/build/release-notes/missing.adoc",
            rendererType = "asciidoc",
            exists = false,
        )

        assertEquals(false, entry.toMap()["exists"])
        assertFalse(entry.exists)
    }

    @Test
    fun `ReleaseNotesArtifactEntry infers rendererType from extension when asciidoc default`() {
        val entry = ReleaseNotesArtifactEntry.fromFile(
            path = "/build/release-notes/release-notes-1.0.0.adoc",
            exists = true,
        )

        assertEquals("asciidoc", entry.rendererType)
    }

    @Test
    fun `ReleaseNotesArtifactEntry infers rendererType markdown from md extension`() {
        val entry = ReleaseNotesArtifactEntry.fromFile(
            path = "/build/release-notes/release-notes-1.0.0.md",
            exists = true,
        )

        assertEquals("markdown", entry.rendererType)
    }

    @Test
    fun `ReleaseNotesArtifactEntry infers rendererType json from json extension`() {
        val entry = ReleaseNotesArtifactEntry.fromFile(
            path = "/build/release-notes/release-notes-1.0.0.json",
            exists = true,
        )

        assertEquals("json", entry.rendererType)
    }
}