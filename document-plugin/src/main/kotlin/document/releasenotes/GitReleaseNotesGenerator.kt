package document.releasenotes

import contracts.pipeline.GitLogParser
import contracts.pipeline.ReleaseNotesConfig
import contracts.pipeline.ReleaseNotesGenerator
import contracts.pipeline.ReleaseNotesRenderer
import document.generation.DocumentLlmProvider
import java.io.File

/**
 * Default [ReleaseNotesGenerator] — orchestrates a [GitLogParser] and a
 * [ReleaseNotesRenderer] to produce a release notes file on disk.
 *
 * DOC-8.2 — Template configurable :
 *   - If a non-null [renderer] is injected via the secondary constructor, it
 *     is used as-is (escape hatch for custom renderers).
 *   - If no renderer is injected (primary constructor), the generator picks
 *     one of the built-in renderers based on [ReleaseNotesConfig.rendererType] :
 *       "asciidoc" → [AsciidocReleaseNotesRenderer] (default)
 *       "markdown" → [MarkdownReleaseNotesRenderer]
 *       "json"     → [JsonReleaseNotesRenderer]
 *     Unknown values fall back to the AsciiDoc renderer.
 *
 * Flow :
 *   1. Resolve [ReleaseNotesConfig.fromTag] (auto-detect if null)
 *   2. Parse commits between fromTag and toTag
 *   3. Render to the selected format
 *   4. Write to `{outputDir}/release-notes-{version}.{ext}`
 *
 * The version is resolved from [ReleaseNotesConfig.version] or auto-detected
 * from the project (VERSION file / gradle.properties).
 */
class GitReleaseNotesGenerator private constructor(
    private val projectDir: File,
    override val parser: GitLogParser,
    override val renderer: ReleaseNotesRenderer,
    private val configDrivenRenderer: Boolean,
    private val llmProvider: DocumentLlmProvider? = null,
) : ReleaseNotesGenerator {

    /** DOC-8.1 constructor — default AsciiDoc renderer (backward compatible). */
    constructor(projectDir: File, parser: GitLogParser) : this(
        projectDir = projectDir,
        parser = parser,
        renderer = AsciidocReleaseNotesRenderer(),
        configDrivenRenderer = false,
    )

    /** DOC-8.1 constructor — explicit renderer injection (custom/escape hatch). */
    constructor(projectDir: File, parser: GitLogParser, renderer: ReleaseNotesRenderer) : this(
        projectDir = projectDir,
        parser = parser,
        renderer = renderer,
        configDrivenRenderer = false,
        llmProvider = null,
    )

    override fun generate(config: ReleaseNotesConfig): File {
        val resolvedVersion = config.version ?: parser.detectVersion(projectDir) ?: "SNAPSHOT"
        val fromTag = config.fromTag ?: parser.detectFromTag(projectDir, config.toTag)
        val commits = if (fromTag != null) {
            parser.parse(fromTag, config.toTag)
        } else {
            parser.parse("", config.toTag)
        }
        val effectiveRenderer = if (configDrivenRenderer) rendererFor(config.rendererType) else renderer
        val outputDir = projectDir.resolve(config.outputDir)
        val outputFile = outputDir.resolve("release-notes-$resolvedVersion.${extensionFor(effectiveRenderer.format)}")
        return effectiveRenderer.renderToFile(commits, config.copy(version = resolvedVersion), outputFile)
    }

    private fun rendererFor(rendererType: String): ReleaseNotesRenderer = when (rendererType) {
        "markdown" -> MarkdownReleaseNotesRenderer()
        "json" -> JsonReleaseNotesRenderer()
        "ollama-asciidoc" -> OllamaAsciidocReleaseNotesRenderer(
            provider = llmProvider ?: throw IllegalStateException(
                "rendererType 'ollama-asciidoc' requires an LLM provider — use configDrivenWithLlm()",
            ),
        )
        else -> AsciidocReleaseNotesRenderer()
    }

    private fun extensionFor(format: String): String = when (format) {
        "markdown" -> "md"
        "json" -> "json"
        else -> "adoc"
    }

    companion object {
        /**
         * DOC-8.2 — Builds a config-driven generator : the renderer is selected
         * at [generate] time from [ReleaseNotesConfig.rendererType]. Use this
         * factory when the DSL/task must honour `rendererType`.
         */
        fun configDriven(projectDir: File, parser: GitLogParser): GitReleaseNotesGenerator =
            GitReleaseNotesGenerator(
                projectDir = projectDir,
                parser = parser,
                renderer = AsciidocReleaseNotesRenderer(),
                configDrivenRenderer = true,
                llmProvider = null,
            )

        /**
         * DOC-8.4 — Config-driven generator with an LLM provider for the
         * `ollama-asciidoc` renderer type. The provider is only invoked if
         * [ReleaseNotesConfig.rendererType] equals `"ollama-asciidoc"`.
         */
        fun configDrivenWithLlm(
            projectDir: File,
            parser: GitLogParser,
            llmProvider: DocumentLlmProvider,
        ): GitReleaseNotesGenerator = GitReleaseNotesGenerator(
            projectDir = projectDir,
            parser = parser,
            renderer = AsciidocReleaseNotesRenderer(),
            configDrivenRenderer = true,
            llmProvider = llmProvider,
        )
    }
}