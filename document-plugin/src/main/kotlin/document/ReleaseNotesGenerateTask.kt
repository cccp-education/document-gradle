package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
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
 * Task `releaseNotesGenerate` (DOC-8.1) — generates release notes AsciiDoc
 * from the git log between two tags.
 *
 * Uses [CliGitLogParser] (git CLI) and [AsciidocReleaseNotesRenderer].
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

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    init {
        group = "document"
        description = "Generates release notes AsciiDoc from git log between two tags (DOC-8)."
    }

    @TaskAction
    fun generate() {
        val projectDir = project.projectDir
        val parser = CliGitLogParser(projectDir)
        val renderer = AsciidocReleaseNotesRenderer()
        val generator = GitReleaseNotesGenerator(projectDir, parser, renderer)

        val config = ReleaseNotesConfig(
            fromTag = fromTag.orNull,
            toTag = toTag.getOrElse("HEAD"),
            version = version.orNull,
            includeDownloads = includeDownloads.getOrElse(true),
            outputDir = outputDir.get().asFile.toRelativeString(projectDir),
        )
        val result = generator.generate(config)
        logger.lifecycle("releaseNotesGenerate -> {}", result.absolutePath)
    }
}