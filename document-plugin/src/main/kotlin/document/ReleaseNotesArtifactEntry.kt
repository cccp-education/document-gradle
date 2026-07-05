package document

import java.io.File

/**
 * A single release-notes artifact produced by the `releaseNotesGenerate` task.
 *
 * Ubiquitous language: a [ReleaseNotesArtifactEntry] describes one release-notes
 * file (AsciiDoc, Markdown, or JSON) produced by the document pipeline from the
 * git log. It is consumed by runner-gradle N3 via the `releaseNotes` array of
 * composite-context.json (DOC-8.3).
 *
 * @property path absolute filesystem path of the release-notes file
 * @property rendererType the renderer that produced the file ("asciidoc", "markdown", "json")
 * @property exists whether the file exists on disk at collection time
 */
data class ReleaseNotesArtifactEntry(
    val path: String,
    val rendererType: String,
    val exists: Boolean,
) {
    /**
     * Serializes this entry to an N3-compatible map.
     *
     * The `source` field identifies the producing borough ("document") and the
     * `type` field marks this as a "release-notes" entry, enabling runner-gradle
     * to distinguish release notes from document artifacts in the composite context.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "source" to "document",
        "path" to path,
        "type" to "release-notes",
        "rendererType" to rendererType,
        "exists" to exists,
    )

    companion object {
        /**
         * Infers the [rendererType] from the file extension of [path].
         *
         * - `.adoc` → "asciidoc"
         * - `.md`   → "markdown"
         * - `.json` → "json"
         * - other   → "asciidoc" (default)
         */
        fun fromFile(path: String, exists: Boolean): ReleaseNotesArtifactEntry {
            val renderer = when (File(path).extension.lowercase()) {
                "adoc" -> "asciidoc"
                "md" -> "markdown"
                "json" -> "json"
                else -> "asciidoc"
            }
            return ReleaseNotesArtifactEntry(path = path, rendererType = renderer, exists = exists)
        }
    }
}