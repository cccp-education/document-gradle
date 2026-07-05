package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Gradle task `collectDocumentRetrieve` (DOC-6 + DOC-8.3).
 *
 * Scans the document pipeline output directory for produced artifacts
 * (generated/enriched AsciiDoc, HTML, PDF, EPUB, DocBook, ManPage) and
 * emits two JSON files for runner-gradle N3 ingestion:
 *
 * - `composite-context.json` — the N3 envelope `{source, entries, count,
 *   releaseNotes, releaseNotesCount, timestamp}` that runner-gradle reads
 *   via `assembleCompositeContext`
 * - `metadata.json` — provenance metadata `{source, type, sessions,
 *   releaseNotesPath, releaseNotesRenderer, ...}`
 *
 * DOC-8.3 — when [releaseNotesDirPath] is configured and points to a
 * directory containing release-notes files produced by `releaseNotesGenerate`,
 * they are indexed in the `releaseNotes` array of composite-context.json,
 * and the first file's path and rendererType are carried in metadata.json
 * so runner-gradle can deploy them via gh-pages.
 *
 * Boundary: this task is a READ-only snapshot of the output directories.
 * It does not invoke the LLM or produce new documents — it only indexes
 * what the other tasks have already written to disk (Loi de l'Economie d'Encre).
 */
@DisableCachingByDefault(because = "Filesystem-bound: scans output directory for produced artifacts")
abstract class CollectDocumentRetrieveTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val sourceAdoc: Property<String>

    @get:Input
    @get:Optional
    abstract val releaseNotesDirPath: Property<String>

    @TaskAction
    fun execute() {
        val dir = outputDir.asFile.get()
        val source = sourceAdoc.get()

        val collector = DocumentArtifactCollector(dir)
        val entries = collector.collect(sourceAdoc = source)

        val releaseNotesEntries = releaseNotesDirPath.orNull
            ?.let { File(it) }
            ?.takeIf { it.exists() && it.isDirectory }
            ?.let { collector.collectReleaseNotes(it) }
            ?: emptyList()

        val result = DocumentRetrieveResult(
            entries = entries,
            releaseNotes = releaseNotesEntries,
        )
        val compositeFile = result.writeTo(dir)

        val primaryReleaseNotes = releaseNotesEntries.firstOrNull()
        val metadata = DocumentMetadata.forNewOrleans(
            type = "retrieve",
            sessions = entries.size,
            dependencies = listOf("brooklyn", "htown"),
            releaseNotesPath = primaryReleaseNotes?.path,
            releaseNotesRenderer = primaryReleaseNotes?.rendererType,
        )
        DocumentMetadata.writeTo(dir, metadata)

        logger.lifecycle(
            "[document] collectDocumentRetrieve — {} artifacts indexed, {} release notes → {}",
            entries.size,
            releaseNotesEntries.size,
            compositeFile.absolutePath
        )
    }
}