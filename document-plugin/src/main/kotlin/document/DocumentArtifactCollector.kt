package document

import java.io.File

/**
 * Scans the document pipeline output directory for produced artifacts.
 *
 * Ubiquitous language: the [DocumentArtifactCollector] inspects the
 * output directory after the pipeline has run and builds a list of
 * [DocumentArtifactEntry] describing every file the document plugin
 * has produced (generated AsciiDoc, enriched AsciiDoc, HTML, PDF,
 * EPUB, DocBook, ManPage).
 *
 * This logic is extracted from the Gradle task to be unit-testable
 * without the Gradle runtime.
 *
 * @property outputDir the directory where the document pipeline writes
 *   its artifacts (typically build/docs/document)
 */
class DocumentArtifactCollector(private val outputDir: File) {

    companion object {
        private val RELEASE_NOTES_EXTENSIONS = setOf("adoc", "md", "json")
    }

    /**
     * Collects all artifacts present in the output directory.
     *
     * S-236 — the collection follows the live `outputFileName` knob (S-235):
     * conversion tasks write `build/docs/document/${outputFileName}.${ext}`, so
     * the scan uses the same (base)name for the generated/enriched AsciiDoc and
     * the 5 format artifacts. Default `"document"` keeps the canonical names —
     * fully backward compatible.
     *
     * @param sourceAdoc the AsciiDoc source filename (for provenance tracking)
     * @param outputFileName the base name of the pipeline outputs (knob `outputFileName`)
     * @return a list of [DocumentArtifactEntry], one per expected artifact,
     *   with [DocumentArtifactEntry.exists] reflecting whether the file
     *   is present on disk
     */
    fun collect(sourceAdoc: String, outputFileName: String = "document"): List<DocumentArtifactEntry> {
        if (!outputDir.exists()) return emptyList()

        val entries = mutableListOf<DocumentArtifactEntry>()

        addIfExists("$outputFileName.adoc", sourceAdoc, entries)
        addIfExists("$outputFileName-enriched.adoc", sourceAdoc, entries)

        DocumentFormat.ALL.forEach { format ->
            val fileName = "$outputFileName.${format.extension}"
            val file = File(outputDir, fileName)
            if (file.exists()) {
                entries.add(DocumentArtifactEntry(
                    path = file.absolutePath,
                    format = format,
                    sourceAdoc = sourceAdoc,
                    exists = true
                ))
            }
        }

        return entries
    }

    /**
     * Collects all release-notes artifacts present in the release-notes output
     * directory (DOC-8.3).
     *
     * Scans for files with extensions `.adoc`, `.md`, `.json` and infers the
     * [ReleaseNotesArtifactEntry.rendererType] from the extension. Files with
     * other extensions (e.g. `.txt`) are skipped.
     *
     * @param releaseNotesDir the directory where `releaseNotesGenerate` writes
     *   its output (typically build/release-notes)
     * @return a list of [ReleaseNotesArtifactEntry], one per release-notes file
     *   present on disk
     */
    fun collectReleaseNotes(releaseNotesDir: File): List<ReleaseNotesArtifactEntry> {
        if (!releaseNotesDir.exists() || !releaseNotesDir.isDirectory) return emptyList()

        return releaseNotesDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in RELEASE_NOTES_EXTENSIONS
        }?.map { file ->
            ReleaseNotesArtifactEntry.fromFile(path = file.absolutePath, exists = true)
        } ?: emptyList()
    }

    private fun addIfExists(fileName: String, sourceAdoc: String, entries: MutableList<DocumentArtifactEntry>) {
        val file = File(outputDir, fileName)
        if (file.exists()) {
            entries.add(DocumentArtifactEntry(
                path = file.absolutePath,
                format = DocumentFormat.HTML,
                sourceAdoc = sourceAdoc,
                exists = true
            ))
        }
    }
}