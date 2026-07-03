package document

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File

/**
 * The N3-compatible retrieve result envelope for the document pipeline.
 *
 * Ubiquitous language: a [DocumentRetrieveResult] aggregates all
 * [DocumentArtifactEntry] produced by the document pipeline into a
 * single JSON file (`composite-context.json`) that runner-gradle N3
 * ingests via `assembleCompositeContext`.
 *
 * The runner reads only the `entries` array from each borough's
 * composite-context.json and merges them into a global context.
 *
 * @property source originating borough slug (always "new-orleans")
 * @property entries list of [DocumentArtifactEntry] produced by the pipeline
 * @property count number of entries
 * @property timestamp epoch milliseconds when this result was assembled
 */
data class DocumentRetrieveResult(
    val source: String = "new-orleans",
    val entries: List<DocumentArtifactEntry>,
    val count: Int = entries.size,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)
    }

    /**
     * Serializes this result to an N3-compatible map.
     *
     * Shape: `{source, entries: [...], count, timestamp}` — runner-gradle
     * reads the `entries` array key.
     */
    fun toMap(): Map<String, Any> = mapOf(
        "source" to source,
        "entries" to entries.map { it.toMap() },
        "count" to count,
        "timestamp" to timestamp
    )

    /**
     * Writes this result as a pretty-printed `composite-context.json`
     * to the given directory.
     *
     * @param dir target directory (created if missing)
     * @return the created [File] handle
     */
    fun writeTo(dir: File): File {
        dir.mkdirs()
        val file = File(dir, "composite-context.json")
        file.writeText(mapper.writeValueAsString(toMap()))
        return file
    }
}