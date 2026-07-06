package document.releasenotes

import contracts.pipeline.ConventionalCommit
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesRenderer
import document.generation.DocumentLlmProvider
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.File

/**
 * AsciiDoc renderer augmented with an IA summary produced by a local LLM
 * (Ollama, port 11437-11465) — DOC-8.4.
 *
 * Decorates [AsciidocReleaseNotesRenderer] by inserting a "== Résumé"
 * section before the conventional-commit categories. The summary is a
 * natural-language synthesis of the commits, produced by the injected
 * [DocumentLlmProvider].
 *
 * Fallback : if the LLM call throws (Ollama not available, timeout, ...),
 * the renderer falls back to the plain Asciidoc renderer output without
 * the "Résumé" section. Loi de l'économie d'encre applies upstream (the
 * task/generator decides whether to re-invoke).
 *
 * The prompt sent to the LLM is a stable, language-agnostic string built
 * from the commit messages, so a deterministic input yields a cacheable
 * output (no metered re-computation).
 */
class OllamaAsciidocReleaseNotesRenderer(
    private val provider: DocumentLlmProvider,
    private val delegate: ReleaseNotesRenderer = AsciidocReleaseNotesRenderer(),
) : ReleaseNotesRenderer {

    private val log = LoggerFactory.getLogger(OllamaAsciidocReleaseNotesRenderer::class.java)

    override val format: String = "asciidoc"

    override fun render(commits: List<ConventionalCommit>, config: ReleaseNotesConfig): String {
        if (commits.isEmpty()) return ""
        val base = delegate.render(commits, config)
        val summary = runCatching { runBlocking { provider.call(buildPrompt(commits)) } }
            .onFailure { log.warn("[OllamaAsciidocReleaseNotesRenderer] LLM summary failed, falling back to plain AsciiDoc: {}", it.message) }
            .getOrNull()
            ?: return base
        return insertSummary(base, summary)
    }

    override fun renderToFile(commits: List<ConventionalCommit>, config: ReleaseNotesConfig, outputFile: File): File {
        val content = render(commits, config)
        outputFile.parentFile.mkdirs()
        outputFile.writeText(content)
        return outputFile
    }

    private fun buildPrompt(commits: List<ConventionalCommit>): String {
        val bulletList = commits.joinToString("\n") { c ->
            val scope = c.scope?.let { " ($it)" } ?: ""
            "- ${c.type}${scope}: ${c.message}"
        }
        return """Summarise the following conventional commits in French in two or three sentences, as a human-readable release overview.
Do not list individual commits — provide a synthesis.

Commits:
$bulletList""".trimIndent()
    }

    private fun insertSummary(base: String, summary: String): String {
        val titleEnd = base.indexOf("\n\n")
        if (titleEnd < 0) return base
        val title = base.substring(0, titleEnd)
        val body = base.substring(titleEnd + 2)
        return "$title\n\n== Résumé\n\n${summary.trim()}\n\n$body"
    }
}