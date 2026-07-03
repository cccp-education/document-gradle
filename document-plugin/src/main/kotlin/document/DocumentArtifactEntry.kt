package document

/**
 * A single document artifact produced by the pipeline.
 *
 * Ubiquitous language: a [DocumentArtifactEntry] describes one output file
 * (HTML, PDF, EPUB, DocBook, ManPage) generated from an AsciiDoc source.
 * It is consumed by runner-gradle N3 via the `entries` array of
 * composite-context.json.
 *
 * @property path absolute filesystem path of the artifact
 * @property format the [DocumentFormat] of the artifact
 * @property sourceAdoc the AsciiDoc source filename the artifact was built from
 * @property exists whether the artifact file exists on disk at collection time
 */
data class DocumentArtifactEntry(
    val path: String,
    val format: DocumentFormat,
    val sourceAdoc: String,
    val exists: Boolean
) {
    /**
     * Serializes this entry to an N3-compatible map.
     *
     * The `source` field identifies the producing borough ("document")
     * within the entry, enabling runner-gradle to merge entries from
     * multiple boroughs into a single composite context.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "source" to "document",
        "path" to path,
        "format" to format.name.lowercase(),
        "backend" to format.backend,
        "sourceAdoc" to sourceAdoc,
        "exists" to exists
    )
}