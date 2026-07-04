package document

import org.gradle.api.provider.Property

/**
 * Nested DSL block `metadata { }` (DOC-12).
 *
 * Front-matter metadata (title, author, language) for the unified
 * `document { }` DSL. Wired by the plugin onto the serializer
 * [DocumentConfigSerializer] for the `document-config.json` N3 contract.
 *
 * Concrete class with eagerly-initialised [Property]s for Kotlin DSL access.
 */
class DocumentMetadataDsl(
    val title: Property<String>,
    val author: Property<String>,
    val language: Property<String>,
)