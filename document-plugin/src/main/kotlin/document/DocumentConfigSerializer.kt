package document

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.KotlinModule
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
 */
class DocumentConfigSerializer {

    private val mapper: ObjectMapper = ObjectMapper()
        .registerModule(KotlinModule.Builder().build())
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

        val file = File(dir, "document-config.json")
        file.writeText(mapper.writeValueAsString(root))
        return file
    }
}