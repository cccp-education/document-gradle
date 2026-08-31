package document

import document.validation.DocumentValidationReport
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

    @get:Input
    @get:Optional
    abstract val validationReportPath: Property<String>

    init {
        // The collect is a *snapshot* of the output directory (DOC-6 contract): its
        // only inputs are constant strings, so the up-to-date checking would skip it
        // even when the snapshot source changed — e.g. `validateDocument` (re)wrote
        // the composite report between two builds of the N3 chain and metadata.json
        // must carry the fresh validationStatus. Re-scan on every invocation (already
        // the spirit of its @DisableCachingByDefault); a non-null @InputFile is not an
        // option because `bookPipeline` dependsOn collect while the report may not
        // exist yet, and a declared missing input fails the build before this action
        // runs (the Gradle pitfall documented in S-232).
        outputs.upToDateWhen { false }
    }

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

        // DOC-METADATA-VALIDATION — carry the composite validation status into
        // metadata.json so runner-gradle N3 can signal it. Read-only snapshot of
        // the report produced by `validateDocument` (Loi de l'Economie d'Encre :
        // no re-validation, just indexing what is already on disk). The report is
        // intentionally a plain string path (not a declared @InputFile): `bookPipeline`
        // dependsOn collect while the report may not exist yet (validate runs in a
        // later build), and a non-null @InputFile would fail the build before this
        // action ever runs — the Gradle pitfall documented in S-232.
        val reportFile: File? = validationReportPath.orNull?.let { File(it) }
            ?.takeIf { it.exists() && it.isFile }
        val validationStatus: String? = reportFile?.let { file ->
            runCatching { DocumentValidationReport.fromJson(file.readText()) }
                .getOrNull()
                ?.overallStatus()
        }

        val primaryReleaseNotes = releaseNotesEntries.firstOrNull()
        val metadata = DocumentMetadata.forNewOrleans(
            type = "retrieve",
            sessions = entries.size,
            dependencies = listOf("brooklyn", "htown"),
            releaseNotesPath = primaryReleaseNotes?.path,
            releaseNotesRenderer = primaryReleaseNotes?.rendererType,
            validationStatus = validationStatus,
        )
        DocumentMetadata.writeTo(dir, metadata)

        logger.lifecycle(
            "[document] collectDocumentRetrieve — {} artifacts indexed, {} release notes, validation={} → {}",
            entries.size,
            releaseNotesEntries.size,
            validationStatus ?: "n/a",
            compositeFile.absolutePath
        )
    }
}