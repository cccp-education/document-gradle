package document

/**
 * Document front-matter metadata (DOC-12).
 *
 * Ubiquitous language: a [DocumentFrontMatter] carries the human-facing
 * metadata of the document (title, author, language) — distinct from the
 * machine-facing [DocumentMetadata] which carries provenance for the N3
 * runner-gradle contract. It is the value object behind the nested
 * `metadata { }` block of the unified `document { }` DSL.
 *
 * Defaults are intentionally non-null and explicit so that serialisation is
 * deterministic (contrat N3 `document-config.json`).
 */
data class DocumentFrontMatter(
    val title: String = "Untitled Document",
    val author: String? = "Unknown Author",
    val language: String = "fr",
) {

    /**
     * Whether every field still holds its default value.
     */
    fun isDefault(): Boolean =
        title == "Untitled Document" &&
            author == "Unknown Author" &&
            language == "fr"

    /**
     * Maps to AsciidoctorJ document attributes.
     *
     * - title -> `docname` (AsciidoctorJ uses the document `= Title` header by
     *   default; this attribute is provided for tooling that consumes the
     *   config without parsing the source AsciiDoc)
     * - author -> `author`
     * - language -> `lang`
     */
    fun toAsciiDocAttributes(): Map<String, String> = buildMap {
        put("docname", title)
        author?.let { put("author", it) }
        put("lang", language)
    }
}