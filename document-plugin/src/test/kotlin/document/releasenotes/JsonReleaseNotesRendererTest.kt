package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.io.File

class JsonReleaseNotesRendererTest {

    private val renderer = JsonReleaseNotesRenderer()

    private fun commit(type: String, scope: String? = null, message: String = "msg") =
        ConventionalCommit(type = type, scope = scope, message = message, hash = "abc", date = "2026-07-05")

    @Test
    fun `format is json`() {
        assertEquals("json", renderer.format)
    }

    @Test
    fun `render returns empty json object when no commits`() {
        val content = renderer.render(emptyList(), ReleaseNotesConfig(version = "1.0.0"))
        assertTrue(content.contains("\"version\""))
        assertTrue(content.contains("\"commits\""))
        assertFalse(content.contains("\"type\""))
    }

    @Test
    fun `render produces version field`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(version = "1.2.0"))
        assertTrue(content.contains("\"version\":\"1.2.0\"") || content.contains("\"version\": \"1.2.0\""))
    }

    @Test
    fun `render uses SNAPSHOT when version is null`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(version = null))
        assertTrue(content.contains("\"version\":\"SNAPSHOT\"") || content.contains("\"version\": \"SNAPSHOT\""))
    }

    @Test
    fun `render includes commits array with type scope message hash date`() {
        val content = renderer.render(
            listOf(commit("feat", scope = "api", message = "hello")),
            ReleaseNotesConfig(version = "1.0.0", includeDownloads = false),
        )
        assertTrue(content.contains("\"type\":\"feat\"") || content.contains("\"type\": \"feat\""))
        assertTrue(content.contains("\"scope\":\"api\"") || content.contains("\"scope\": \"api\""))
        assertTrue(content.contains("\"message\":\"hello\"") || content.contains("\"message\": \"hello\""))
        assertTrue(content.contains("\"hash\":\"abc\"") || content.contains("\"hash\": \"abc\""))
        assertTrue(content.contains("\"date\":\"2026-07-05\"") || content.contains("\"date\": \"2026-07-05\""))
    }

    @Test
    fun `render groups commits by category under categories object`() {
        val commits = listOf(
            commit("feat", message = "feature 1"),
            commit("fix", message = "bug 1"),
        )
        val content = renderer.render(commits, ReleaseNotesConfig(version = "1.0.0", includeDownloads = false))
        assertTrue(content.contains("\"Nouveautés\"") || content.contains("\"Nouveaut\\u00e9s\""))
        assertTrue(content.contains("\"Corrections\"") || content.contains("\"Corrections\""))
    }

    @Test
    fun `render includes downloads field when includeDownloads is true`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(includeDownloads = true))
        assertTrue(content.contains("\"includeDownloads\":true") || content.contains("\"includeDownloads\": true"))
    }

    @Test
    fun `render omits downloads field when includeDownloads is false`() {
        val content = renderer.render(listOf(commit("feat")), ReleaseNotesConfig(includeDownloads = false))
        assertFalse(content.contains("\"includeDownloads\":true"))
        assertFalse(content.contains("\"includeDownloads\": true"))
    }

    @Test
    fun `renderToFile writes content to file and returns it`(@TempDir dir: File) {
        val output = dir.resolve("release-notes.json")
        val result = renderer.renderToFile(listOf(commit("feat", message = "hello")), ReleaseNotesConfig(version = "1.0.0"), output)
        assertEquals(output, result)
        assertTrue(output.isFile)
        assertTrue(output.readText().contains("\"message\":\"hello\"") || output.readText().contains("\"message\": \"hello\""))
    }

    @Test
    fun `renderToFile creates parent directories`(@TempDir dir: File) {
        val output = dir.resolve("build/release-notes/v1.json")
        renderer.renderToFile(listOf(commit("feat")), ReleaseNotesConfig(), output)
        assertTrue(output.isFile)
    }
}