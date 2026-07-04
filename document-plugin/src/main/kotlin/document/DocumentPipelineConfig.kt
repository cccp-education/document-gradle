package document

import java.io.File

/**
 * Unified pipeline configuration (DOC-12).
 *
 * Ubiquitous language: a [DocumentPipelineConfig] is the single value object
 * materialising the unified `document { }` DSL block. It aggregates the
 * source, the enrichment toggles, the output format toggles, the visual
 * theme, and the front-matter metadata. It is serialisable to the N3
 * contract file `document-config.json` via [DocumentConfigSerializer].
 *
 * This object supersedes the flat [DocumentConfig] for the unified DSL path
 * while remaining backward compatible with the existing task wiring.
 */
data class DocumentPipelineConfig(
    val source: DocumentSource,
    val enrich: DocumentEnrichConfig = DocumentEnrichConfig(),
    val outputs: DocumentOutputs = DocumentOutputs(),
    val theme: DocumentTheme = DocumentTheme(),
    val frontMatter: DocumentFrontMatter = DocumentFrontMatter(),
) {

    /**
     * Output directory for the pipeline artefacts (defaults to `build/docs/document`).
     */
    var outputDir: File? = null
}