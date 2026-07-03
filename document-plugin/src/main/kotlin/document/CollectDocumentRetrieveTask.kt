package document

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/**
 * Gradle task `collectDocumentRetrieve` (DOC-6).
 *
 * Scans the document pipeline output directory for produced artifacts
 * (generated/enriched AsciiDoc, HTML, PDF, EPUB, DocBook, ManPage) and
 * emits two JSON files for runner-gradle N3 ingestion:
 *
 * - `composite-context.json` — the N3 envelope `{source, entries, count}`
 *   that runner-gradle reads via `assembleCompositeContext`
 * - `metadata.json` — provenance metadata `{source, type, sessions, ...}`
 *
 * Boundary: this task is a READ-only snapshot of the output directory.
 * It does not invoke the LLM or produce new documents — it only indexes
 * what the other tasks (generateDocument, enrichDocument, convertXxx)
 * have already written to disk (Loi de l'Economie d'Encre).
 */
@DisableCachingByDefault(because = "Filesystem-bound: scans output directory for produced artifacts")
abstract class CollectDocumentRetrieveTask : DefaultTask() {

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:Input
    abstract val sourceAdoc: Property<String>

    @TaskAction
    fun execute() {
        val dir = outputDir.asFile.get()
        val source = sourceAdoc.get()

        val collector = DocumentArtifactCollector(dir)
        val entries = collector.collect(sourceAdoc = source)

        val result = DocumentRetrieveResult(entries = entries)
        val compositeFile = result.writeTo(dir)

        val metadata = DocumentMetadata.forNewOrleans(
            type = "retrieve",
            sessions = entries.size,
            dependencies = listOf("brooklyn", "htown")
        )
        DocumentMetadata.writeTo(dir, metadata)

        logger.lifecycle(
            "[document] collectDocumentRetrieve — {} artifacts indexed → {}",
            entries.size,
            compositeFile.absolutePath
        )
    }
}