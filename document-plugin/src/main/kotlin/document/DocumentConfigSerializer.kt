package document

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.File

/**
 * Serialises a [DocumentPipelineConfig] to `document-config.json` (DOC-12).
 *
 * The JSON is the N3 contract consumed by runner-gradle and downstream
 * tooling. It mirrors the structure of the unified `document { }` DSL:
 *
 * ```
 * {
 *   "source"  : "livre.adoc",
 *   "enrich"  : { "plantuml": false, "images": false, "passthrough": false },
 *   "outputs" : { "html": true, "pdf": false, "epub": false, "docbook": false, "manpage": false },
 *   "theme"   : { "pdfTheme": null, "htmlStylesheet": null, "epubStylesheet": null, "logo": null },
 *   "metadata": { "title": "...", "author": "...", "language": "fr" }
 * }
 * ```
 *
 * DOC-12 round-trip: the [deserialize] method reads a `document-config.json`
 * file back into a [DocumentPipelineConfig]. The round-trip is idempotent —
 * serialising then deserialising then re-serialising produces byte-identical
 * output. The `source` and theme/book paths are resolved against [baseDir]
 * (the project directory hosting the JSON file).
 */
class DocumentConfigSerializer {

    private val mapper: ObjectMapper = ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT)

    /**
     * Serialises [config] to `document-config.json` inside [dir].
     *
     * @param dir target directory (created if missing)
     * @param config the pipeline configuration to serialise
     * @return the written file handle
     */
    fun serialize(dir: File, config: DocumentPipelineConfig): File {
        dir.mkdirs()
        val root: ObjectNode = mapper.createObjectNode()

        root.put("source", config.source.file.name)

        val enrich = mapper.createObjectNode()
        enrich.put("plantuml", config.enrich.plantuml)
        enrich.put("images", config.enrich.images)
        enrich.put("passthrough", config.enrich.passthrough)
        root.set<ObjectNode>("enrich", enrich)

        val outputs = mapper.createObjectNode()
        outputs.put("html", config.outputs.html)
        outputs.put("pdf", config.outputs.pdf)
        outputs.put("epub", config.outputs.epub)
        outputs.put("docbook", config.outputs.docbook)
        outputs.put("manpage", config.outputs.manpage)
        root.set<ObjectNode>("outputs", outputs)

        val theme = mapper.createObjectNode()
        theme.put("pdfTheme", config.theme.pdfTheme?.absolutePath)
        theme.put("htmlStylesheet", config.theme.htmlStylesheet?.absolutePath)
        theme.put("epubStylesheet", config.theme.epubStylesheet?.absolutePath)
        theme.put("logo", config.theme.logo?.absolutePath)
        root.set<ObjectNode>("theme", theme)

        val metadata = mapper.createObjectNode()
        metadata.put("title", config.frontMatter.title)
        metadata.put("author", config.frontMatter.author)
        metadata.put("language", config.frontMatter.language)
        root.set<ObjectNode>("metadata", metadata)

        if (!config.book.isEmpty()) {
            val book = mapper.createObjectNode()
            book.put("pagesDir", config.book.pagesDir?.absolutePath)
            book.put("photosDir", config.book.photosDir?.absolutePath)
            book.put("title", config.book.title)
            book.put("author", config.book.author)
            root.set<ObjectNode>("book", book)
        }

        val file = File(dir, "document-config.json")
        file.writeText(mapper.writeValueAsString(root))
        return file
    }

    /**
     * Deserialises a `document-config.json` file back into a [DocumentPipelineConfig].
     *
     * DOC-12 round-trip: this is the mirror of [serialize]. The `source` field
     * is resolved against [baseDir] (the project directory). Theme paths and
     * book directories are resolved against [baseDir] too. Missing optional
     * blocks (theme, book) fall back to their defaults.
     *
     * @param file the `document-config.json` file to read
     * @param baseDir the directory used to resolve relative paths (source, theme, book)
     * @return the reconstructed [DocumentPipelineConfig]
     */
    fun deserialize(file: File, baseDir: File): DocumentPipelineConfig {
        val root = mapper.readTree(file)

        val sourceName = root.get("source")?.asText()
            ?: throw IllegalStateException("document-config.json is missing the 'source' field")
        val sourceFile = File(baseDir, sourceName)
        val source = DocumentSource(sourceFile)

        val enrich = DocumentEnrichConfig(
            plantuml = root.get("enrich")?.get("plantuml")?.asBoolean() ?: false,
            images = root.get("enrich")?.get("images")?.asBoolean() ?: false,
            passthrough = root.get("enrich")?.get("passthrough")?.asBoolean() ?: false,
        )

        val outputsNode = root.get("outputs")
        val outputs = DocumentOutputs(
            html = outputsNode?.get("html")?.asBoolean() ?: true,
            pdf = outputsNode?.get("pdf")?.asBoolean() ?: false,
            epub = outputsNode?.get("epub")?.asBoolean() ?: false,
            docbook = outputsNode?.get("docbook")?.asBoolean() ?: false,
            manpage = outputsNode?.get("manpage")?.asBoolean() ?: false,
        )

        val themeNode = root.get("theme")
        val theme = DocumentTheme(
            pdfTheme = themeNode?.get("pdfTheme")?.textValueOrNull()?.let { File(it) },
            htmlStylesheet = themeNode?.get("htmlStylesheet")?.textValueOrNull()?.let { File(it) },
            epubStylesheet = themeNode?.get("epubStylesheet")?.textValueOrNull()?.let { File(it) },
            logo = themeNode?.get("logo")?.textValueOrNull()?.let { File(it) },
        )

        val metadataNode = root.get("metadata")
        val frontMatter = DocumentFrontMatter(
            title = metadataNode?.get("title")?.textValueOrNull() ?: "Untitled Document",
            author = metadataNode?.get("author")?.textValueOrNull() ?: "Unknown Author",
            language = metadataNode?.get("language")?.textValueOrNull() ?: "fr",
        )

        val bookNode = root.get("book")
        val book = if (bookNode != null && !bookNode.isNull) {
            BookConfig(
                pagesDir = bookNode.get("pagesDir")?.textValueOrNull()?.let { File(it) },
                photosDir = bookNode.get("photosDir")?.textValueOrNull()?.let { File(it) },
                title = bookNode.get("title")?.textValueOrNull(),
                author = bookNode.get("author")?.textValueOrNull(),
            )
        } else {
            BookConfig()
        }

        return DocumentPipelineConfig(
            source = source,
            enrich = enrich,
            outputs = outputs,
            theme = theme,
            frontMatter = frontMatter,
            book = book,
        )
    }
}

/**
 * Returns the textual value of a [JsonNode] or `null` when the node is absent,
 * null-typed, or non-textual. This is the null-safe mirror of [JsonNode.asText]
 * — `asText` returns the string `"null"` for explicit JSON `null` values,
 * whereas this helper returns a Kotlin `null` so downstream `?.let { File(it) }`
 * chains do not create a `File("null")` pointing to a phantom path.
 */
private fun JsonNode?.textValueOrNull(): String? =
    if (this == null || isNull) null else textValue()