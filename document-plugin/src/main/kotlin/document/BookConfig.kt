package document

import java.io.File

/**
 * Book pipeline configuration value object (DOC-12 extension — `book { }` serialisation).
 *
 * Ubiquitous language: a [BookConfig] materialises the four book pipeline
 * properties (DOC-11) of the unified `document { }` DSL into a serialisable
 * value object. It mirrors [BookDsl] (Gradle properties) for the N3 contract
 * `document-config.json` consumed by runner-gradle.
 *
 * The properties default to `null` (unset) so that serialisation omits them
 * when no book pipeline is configured (JsonInclude.NON_NULL on the consumer
 * side). When the book block is present, the four fields are serialised
 * alongside the other pipeline sections.
 *
 * ```
 * "book": {
 *   "pagesDir": "src/book/pages",
 *   "photosDir": "src/book/photos",
 *   "title": "Mon Livre",
 *   "author": "Auteur"
 * }
 * ```
 */
data class BookConfig(
    val pagesDir: File? = null,
    val photosDir: File? = null,
    val title: String? = null,
    val author: String? = null,
) {

    /**
     * `true` when no book pipeline is configured — the serialiser omits the block.
     *
     * The book pipeline is considered configured only when at least one of the
     * directories (pages or photos) is set. The title and author default to
     * "Untitled Book" / "Unknown Author" via Gradle conventions, so they are
     * not reliable signals of an active book pipeline.
     */
    fun isEmpty(): Boolean = pagesDir == null && photosDir == null
}