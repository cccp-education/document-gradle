package document

/**
 * Enrichment configuration for the document pipeline (DOC-12).
 *
 * Ubiquitous language: a [DocumentEnrichConfig] groups the three enrichment
 * toggles (PlantUML, images, passthrough) previously scattered as flat
 * properties on the extension. It is the value object behind the nested
 * `enrich { }` block of the unified `document { }` DSL.
 *
 * - [plantuml] : preserve inline `[plantuml]` blocks (DOC-9)
 * - [images] : embed original photos as `image::` directives (DOC-11)
 * - [passthrough] : preserve raw HTML `++++` blocks without escaping (DOC-9)
 */
data class DocumentEnrichConfig(
    val plantuml: Boolean = false,
    val images: Boolean = false,
    val passthrough: Boolean = false,
) {

    /**
     * Whether any enrichment step is active.
     */
    fun requiresEnrichment(): Boolean = plantuml || images || passthrough
}