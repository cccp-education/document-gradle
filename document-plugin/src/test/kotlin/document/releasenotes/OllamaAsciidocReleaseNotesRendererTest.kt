package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import document.generation.DocumentLlmProvider
import document.generation.FakeDocumentLlmProvider
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.io.File

class OllamaAsciidocReleaseNotesRendererTest {

    private val summary = "Cette version apporte deux nouveautés majeures et une correction."

    private fun commit(type: String, scope: String? = null, message: String = "msg") =
        ConventionalCommit(type = type, scope = scope, message = message, hash = "h", date = "d")

    private val fakeProvider: DocumentLlmProvider = FakeDocumentLlmProvider(summary)

    private val renderer = OllamaAsciidocReleaseNotesRenderer(fakeProvider)

    @Test
    fun `format is asciidoc`() {
        assertEquals("asciidoc", renderer.format)
    }

    @Test
    fun `render returns empty string when no commits`() {
        assertEquals("", renderer.render(emptyList(), ReleaseNotesConfig()))
    }

    @Test
    fun `render includes IA summary section before categories`() {
        val commits = listOf(
            commit("feat", message = "feature 1"),
            commit("fix", message = "bug 1"),
        )
        val content = renderer.render(commits, ReleaseNotesConfig(version = "1.2.0"))
        assertTrue(content.contains("= Release Notes 1.2.0"))
        assertTrue(content.contains("== Résumé"))
        assertTrue(content.contains(summary))
        val summaryIdx = content.indexOf("== Résumé")
        val featIdx = content.indexOf("== Nouveautés")
        assertTrue(summaryIdx in 0 until featIdx, "Résumé should appear before categories")
    }

    @Test
    fun `render falls back to asciidoc renderer when LLM throws`() {
        val failingProvider = object : DocumentLlmProvider {
            override suspend fun call(prompt: String): String = throw RuntimeException("Ollama unavailable")
        }
        val failingRenderer = OllamaAsciidocReleaseNotesRenderer(failingProvider)
        val content = failingRenderer.render(listOf(commit("feat", message = "hello")), ReleaseNotesConfig())
        assertTrue(content.contains("= Release Notes"))
        assertTrue(content.contains("- hello"))
        assertFalse(content.contains("== Résumé"))
    }

    @Test
    fun `renderToFile writes content to file and returns it`(@TempDir dir: File) {
        val output = dir.resolve("release-notes.adoc")
        val result = renderer.renderToFile(
            listOf(commit("feat", message = "hello")),
            ReleaseNotesConfig(version = "1.0.0"),
            output,
        )
        assertEquals(output, result)
        assertTrue(output.isFile)
        assertTrue(output.readText().contains("= Release Notes 1.0.0"))
        assertTrue(output.readText().contains("== Résumé"))
        assertTrue(output.readText().contains("- hello"))
    }

    @Test
    fun `renderToFile creates parent directories`(@TempDir dir: File) {
        val output = dir.resolve("build/release-notes/v1.adoc")
        renderer.renderToFile(listOf(commit("feat")), ReleaseNotesConfig(), output)
        assertTrue(output.isFile)
    }
}