package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.io.File

class AsciidocReleaseNotesRendererTest {

    private val renderer = AsciidocReleaseNotesRenderer()

    private fun commit(type: String, scope: String? = null, message: String = "msg") =
        ConventionalCommit(type = type, scope = scope, message = message, hash = "h", date = "d")

    @Test
    fun `format is asciidoc`() {
        assertEquals("asciidoc", renderer.format)
    }

    @Test
    fun `render returns empty string when no commits`() {
        val content = renderer.render(emptyList(), ReleaseNotesConfig())
        assertEquals("", content)
    }

    @Test
    fun `render produces title with version`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(version = "1.2.0"))
        assertTrue(content.contains("= Release Notes 1.2.0"))
    }

    @Test
    fun `render uses SNAPSHOT when version is null`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(version = null))
        assertTrue(content.contains("= Release Notes SNAPSHOT"))
    }

    @Test
    fun `render groups commits by category in config order`() {
        val commits = listOf(
            commit("fix", message = "bug 1"),
            commit("feat", message = "feature 1"),
            commit("fix", message = "bug 2"),
        )
        val content = renderer.render(commits, ReleaseNotesConfig())
        val featIndex = content.indexOf("== Nouveautés")
        val fixIndex = content.indexOf("== Corrections")
        assertTrue(featIndex < fixIndex, "feat should appear before fix")
        assertTrue(content.contains("- feature 1"))
        assertTrue(content.contains("- bug 1"))
        assertTrue(content.contains("- bug 2"))
    }

    @Test
    fun `render includes scope in parentheses when present`() {
        val content = renderer.render(listOf(commit("fix", scope = "api", message = "bug")), ReleaseNotesConfig())
        assertTrue(content.contains("- bug (api)"))
    }

    @Test
    fun `render omits empty categories`() {
        val commits = listOf(commit("feat", message = "only feat"))
        val content = renderer.render(commits, ReleaseNotesConfig())
        assertTrue(content.contains("== Nouveautés"))
        assertFalse(content.contains("== Corrections"))
    }

    @Test
    fun `render includes download section when includeDownloads is true`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(includeDownloads = true))
        assertTrue(content.contains("== Téléchargement"))
    }

    @Test
    fun `render omits download section when includeDownloads is false`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(includeDownloads = false))
        assertFalse(content.contains("== Téléchargement"))
    }

    @Test
    fun `renderToFile writes content to file and returns it`(@TempDir dir: File) {
        val output = dir.resolve("release-notes.adoc")
        val result = renderer.renderToFile(listOf(commit("feat", message = "hello")), ReleaseNotesConfig(version = "1.0.0"), output)
        assertEquals(output, result)
        assertTrue(output.isFile)
        assertTrue(output.readText().contains("= Release Notes 1.0.0"))
        assertTrue(output.readText().contains("- hello"))
    }

    @Test
    fun `renderToFile creates parent directories`(@TempDir dir: File) {
        val output = dir.resolve("build/release-notes/v1.adoc")
        renderer.renderToFile(listOf(commit("feat")), ReleaseNotesConfig(), output)
        assertTrue(output.isFile)
    }
}