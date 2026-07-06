package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.GitLogParser
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesRenderer
import document.generation.FakeDocumentLlmProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.Test
import java.io.File

class GitReleaseNotesGeneratorTest {

    @TempDir
    lateinit var projectDir: File

    private val fakeParser = object : GitLogParser {
        override fun parse(fromTag: String, toTag: String): List<ConventionalCommit> = listOf(
            ConventionalCommit("feat", scope = "api", message = "feature 1", hash = "h1", date = "d1"),
            ConventionalCommit("fix", scope = null, message = "bug 1", hash = "h2", date = "d2"),
        )
        override fun detectVersion(projectDir: File): String? = "1.5.0"
        override fun detectFromTag(projectDir: File, toTag: String): String? = "v1.4.0"
    }

    private val stubRenderer = object : ReleaseNotesRenderer {
        override val format: String = "asciidoc"
        override fun render(commits: List<ConventionalCommit>, config: ReleaseNotesConfig): String =
            "RENDERED ${commits.size} commits version=${config.version}"
        override fun renderToFile(commits: List<ConventionalCommit>, config: ReleaseNotesConfig, outputFile: File): File {
            outputFile.parentFile.mkdirs()
            outputFile.writeText(render(commits, config))
            return outputFile
        }
    }

    @Test
    fun `generate writes release notes file with version from detectVersion`() {
        val generator = GitReleaseNotesGenerator(projectDir, fakeParser, stubRenderer)
        val result = generator.generate(ReleaseNotesConfig())
        assertTrue(result.isFile)
        assertEquals("release-notes-1.5.0.adoc", result.name)
        assertTrue(result.readText().contains("RENDERED 2 commits version=1.5.0"))
    }

    @Test
    fun `generate uses config version when provided`() {
        val generator = GitReleaseNotesGenerator(projectDir, fakeParser, stubRenderer)
        val result = generator.generate(ReleaseNotesConfig(version = "9.9.9"))
        assertEquals("release-notes-9.9.9.adoc", result.name)
    }

    @Test
    fun `generate uses SNAPSHOT when no version detected and no config version`() {
        val noVersionParser = object : GitLogParser {
            override fun parse(fromTag: String, toTag: String): List<ConventionalCommit> = emptyList()
            override fun detectVersion(projectDir: File): String? = null
            override fun detectFromTag(projectDir: File, toTag: String): String? = null
        }
        val generator = GitReleaseNotesGenerator(projectDir, noVersionParser, stubRenderer)
        val result = generator.generate(ReleaseNotesConfig())
        assertEquals("release-notes-SNAPSHOT.adoc", result.name)
    }

    @Test
    fun `generate parses from detected fromTag when config fromTag is null`() {
        var capturedFrom: String? = null
        var capturedTo: String? = null
        val spyParser = object : GitLogParser {
            override fun parse(fromTag: String, toTag: String): List<ConventionalCommit> {
                capturedFrom = fromTag
                capturedTo = toTag
                return emptyList()
            }
            override fun detectVersion(projectDir: File): String? = "2.0.0"
            override fun detectFromTag(projectDir: File, toTag: String): String? = "v1.9.0"
        }
        val generator = GitReleaseNotesGenerator(projectDir, spyParser, stubRenderer)
        generator.generate(ReleaseNotesConfig(toTag = "HEAD"))
        assertEquals("v1.9.0", capturedFrom)
        assertEquals("HEAD", capturedTo)
    }

    @Test
    fun `generate writes to configured outputDir`() {
        val generator = GitReleaseNotesGenerator(projectDir, fakeParser, stubRenderer)
        val result = generator.generate(ReleaseNotesConfig(outputDir = "build/notes"))
        assertTrue(result.path.replace('\\', '/').endsWith("build/notes/release-notes-1.5.0.adoc"))
    }

    @Test
    fun `generate selects markdown renderer by config rendererType and produces md extension`() {
        val generator = GitReleaseNotesGenerator.configDriven(projectDir, fakeParser)
        val result = generator.generate(ReleaseNotesConfig(rendererType = "markdown"))
        assertTrue(result.isFile)
        assertEquals("release-notes-1.5.0.md", result.name)
        assertTrue(result.readText().contains("# Release Notes 1.5.0"))
    }

    @Test
    fun `generate selects json renderer by config rendererType and produces json extension`() {
        val generator = GitReleaseNotesGenerator.configDriven(projectDir, fakeParser)
        val result = generator.generate(ReleaseNotesConfig(rendererType = "json", includeDownloads = false))
        assertTrue(result.isFile)
        assertEquals("release-notes-1.5.0.json", result.name)
        assertTrue(result.readText().contains("\"version\":\"1.5.0\""))
    }

    @Test
    fun `generate falls back to asciidoc renderer for unknown rendererType`() {
        val generator = GitReleaseNotesGenerator.configDriven(projectDir, fakeParser)
        val result = generator.generate(ReleaseNotesConfig(rendererType = "unknown-format"))
        assertTrue(result.isFile)
        assertEquals("release-notes-1.5.0.adoc", result.name)
    }

    @Test
    fun `generate uses injected renderer when provided`() {
        val generator = GitReleaseNotesGenerator(projectDir, fakeParser, stubRenderer)
        val result = generator.generate(ReleaseNotesConfig(rendererType = "markdown"))
        assertEquals("release-notes-1.5.0.adoc", result.name)
        assertTrue(result.readText().contains("RENDERED 2 commits version=1.5.0"))
    }

    // --- DOC-8.4 — ollama-asciidoc renderer wiring ---

    @Test
    fun `configDrivenWithLlm produces asciidoc with IA summary when rendererType is ollama-asciidoc`() {
        val generator = GitReleaseNotesGenerator.configDrivenWithLlm(
            projectDir = projectDir,
            parser = fakeParser,
            llmProvider = FakeDocumentLlmProvider("Cette version apporte deux changements."),
        )
        val result = generator.generate(ReleaseNotesConfig(rendererType = "ollama-asciidoc"))
        assertTrue(result.isFile)
        assertEquals("release-notes-1.5.0.adoc", result.name)
        val content = result.readText()
        assertTrue(content.contains("== Résumé"))
        assertTrue(content.contains("Cette version apporte deux changements."))
        assertTrue(content.contains("== Nouveautés"))
    }

    @Test
    fun `configDrivenWithLlm falls back to plain asciidoc when LLM throws`() {
        val failingProvider = object : document.generation.DocumentLlmProvider {
            override suspend fun call(prompt: String): String = throw RuntimeException("boom")
        }
        val generator = GitReleaseNotesGenerator.configDrivenWithLlm(
            projectDir = projectDir,
            parser = fakeParser,
            llmProvider = failingProvider,
        )
        val result = generator.generate(ReleaseNotesConfig(rendererType = "ollama-asciidoc"))
        assertTrue(result.isFile)
        val content = result.readText()
        assertTrue(content.contains("= Release Notes"))
        assertTrue(!content.contains("== Résumé"))
    }

    @Test
    fun `configDrivenWithLlm still routes to markdown when rendererType is markdown`() {
        val generator = GitReleaseNotesGenerator.configDrivenWithLlm(
            projectDir = projectDir,
            parser = fakeParser,
            llmProvider = FakeDocumentLlmProvider(),
        )
        val result = generator.generate(ReleaseNotesConfig(rendererType = "markdown"))
        assertEquals("release-notes-1.5.0.md", result.name)
        assertTrue(result.readText().contains("# Release Notes 1.5.0"))
    }
}