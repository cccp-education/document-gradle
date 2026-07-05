package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import document.releasenotes.AsciidocReleaseNotesRenderer
import document.releasenotes.CliGitLogParser
import document.releasenotes.GitReleaseNotesGenerator
import contracts.pipeline.ReleaseNotesConfig

/**
 * Task `releaseNotesGenerate` (DOC-8.1 + DOC-8.2) — generates release notes
 * AsciiDoc/Markdown/JSON from the git log between two tags.
 *
 * DOC-8.2 — the [rendererType] selects the output format ("asciidoc" default,
 * "markdown", "json") and the optional [categories] overrides the default
 * Conventional Commit category labels.
 *
 * Uses [CliGitLogParser] (git CLI) and a config-driven [GitReleaseNotesGenerator].
 * The project directory is the working directory of the git commands.
 */
@DisableCachingByDefault(because = "Git log is non-deterministic across runs")
abstract class ReleaseNotesGenerateTask : DefaultTask() {

    @get:Input
    @get:Optional
    abstract val fromTag: Property<String>

    @get:Input
    abstract val toTag: Property<String>

    @get:Input
    @get:Optional
    abstract val version: Property<String>

    @get:Input
    abstract val includeDownloads: Property<Boolean>

    @get:Input
    abstract val rendererType: Property<String>

    @get:Input
    abstract val categories: MapProperty<String, String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "document"
        description = "Generates release notes AsciiDoc/Markdown/JSON from git log between two tags (DOC-8)."
    }

    @TaskAction
    fun generate() {
        val projectDir = project.projectDir
        val parser = CliGitLogParser(projectDir)
        val generator = GitReleaseNotesGenerator.configDriven(projectDir, parser)

        val customCategories = categories.get().takeIf { it.isNotEmpty() }
        val config = ReleaseNotesConfig(
            fromTag = fromTag.orNull,
            toTag = toTag.getOrElse("HEAD"),
            version = version.orNull,
            includeDownloads = includeDownloads.getOrElse(true),
            outputDir = outputDir.get().asFile.toRelativeString(projectDir),
            rendererType = rendererType.getOrElse("asciidoc"),
            categories = customCategories ?: ReleaseNotesConfig().categories,
        )
        val result = generator.generate(config)
        logger.lifecycle("releaseNotesGenerate -> {}", result.absolutePath)
    }
}