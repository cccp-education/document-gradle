package document

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Nested DSL block `book { }` (DOC-12 extension — book pipeline).
 *
 * Groups the four book pipeline properties (DOC-11) in the unified
 * `document { }` DSL. The properties default to unset and are wired by
 * the plugin registration onto the legacy flat properties of
 * [DocumentExtension] (bookPagesDir, bookPhotosDir, bookTitle,
 * bookAuthor) so existing tasks (`assembleBook`, `bookPipeline`) stay
 * unchanged.
 *
 * ```
 * document {
 *     book {
 *         pagesDir.set(file("src/book/pages"))
 *         photosDir.set(file("src/book/photos"))
 *         title.set("Mon Livre")
 *         author.set("Auteur")
 *     }
 * }
 * ```
 *
 * Concrete class with eagerly-initialised [Property]s for Kotlin DSL
 * access (pattern [DocumentEnrichDsl] / [DocumentOutputsDsl]).
 */
class BookDsl(
    val pagesDir: DirectoryProperty,
    val photosDir: DirectoryProperty,
    val title: Property<String>,
    val author: Property<String>,
)