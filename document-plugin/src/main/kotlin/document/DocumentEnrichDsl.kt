package document

import org.gradle.api.provider.Property

/**
 * Nested DSL block `enrich { }` (DOC-12).
 *
 * Groups the three enrichment toggles in the unified `document { }` DSL.
 * The properties default to false and are wired by the plugin registration
 * onto the legacy flat properties of [DocumentExtension] (enrichPlantUml,
 * enrichImages, enrichPassthrough) so existing tasks stay unchanged.
 *
 * Concrete class with eagerly-initialised [Property]s so the Kotlin DSL
 * resolves `plantuml`, `images` and `passthrough` inside the block without
 * requiring Gradle-managed-type generation.
 */
class DocumentEnrichDsl(
    val plantuml: Property<Boolean>,
    val images: Property<Boolean>,
    val passthrough: Property<Boolean>,
)