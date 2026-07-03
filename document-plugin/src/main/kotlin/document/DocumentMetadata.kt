package document

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.io.File
import java.time.Instant

/**
 * Metadata descriptor for document pipeline artifacts.
 *
 * Carries provenance information (source borough, model version, dependencies)
 * attached to every generated output — HTML, PDF, EPUB, DocBook, ManPage.
 *
 * Conforms to the de facto N3 runner-gradle contract: this metadata.json
 * is written alongside composite-context.json and ingested by
 * `assembleCompositeContext` in runner-gradle (N3).
 *
 * @property source originating borough slug (always "new-orleans" for Document)
 * @property type pipeline stage type (e.g. "retrieve", "composite-context")
 * @property sessions number of artifact entries in the companion JSON
 * @property generatedAt ISO-8601 timestamp of generation
 * @property model embedding model name (e.g. "onnx-local")
 * @property version artifact version string (always "1.0")
 * @property dependencies list of upstream borough slugs this artifact depends on
 */
data class DocumentMetadata(
    val source: String,
    val type: String,
    val sessions: Int,
    val generatedAt: String,
    val model: String,
    val version: String,
    val dependencies: List<String>
) {
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper()
            .registerModule(KotlinModule.Builder().build())
            .enable(SerializationFeature.INDENT_OUTPUT)

        /**
         * Writes metadata as a pretty-printed JSON file to the given directory.
         *
         * @param dir target directory (created if missing)
         * @param metadata the metadata to persist
         * @return the created [File] handle
         */
        fun writeTo(dir: File, metadata: DocumentMetadata): File {
            dir.mkdirs()
            val file = File(dir, "metadata.json")
            file.writeText(mapper.writeValueAsString(metadata))
            return file
        }

        /**
         * Creates a standard DocumentMetadata instance for the New Orleans borough.
         *
         * @param type pipeline stage type (default: "retrieve")
         * @param model embedding model name (default: "onnx-local")
         * @param sessions number of artifact entries (default: 0)
         * @param dependencies upstream borough dependencies
         *        (default: ["brooklyn", "htown"] — Codex provides OCR AsciiDoc,
         *         plantuml-gradle provides diagrams)
         * @return a new [DocumentMetadata] with source set to "new-orleans"
         */
        fun forNewOrleans(
            type: String = "retrieve",
            model: String = "onnx-local",
            sessions: Int = 0,
            dependencies: List<String> = listOf("brooklyn", "htown")
        ): DocumentMetadata = DocumentMetadata(
            source = "new-orleans",
            type = type,
            sessions = sessions,
            generatedAt = Instant.now().toString(),
            model = model,
            version = "1.0",
            dependencies = dependencies
        )
    }
}