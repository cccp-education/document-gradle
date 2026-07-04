package document

import org.gradle.api.provider.Property

/**
 * Nested DSL block `outputs { }` (DOC-12).
 *
 * Selects which AsciidoctorJ backends to invoke via five boolean toggles.
 * The plugin maps the enabled flags to the legacy [DocumentExtension.formats]
 * list so existing conversion tasks stay unchanged.
 *
 * Concrete class with eagerly-initialised [Property]s for Kotlin DSL access.
 */
class DocumentOutputsDsl(
    val html: Property<Boolean>,
    val pdf: Property<Boolean>,
    val epub: Property<Boolean>,
    val docbook: Property<Boolean>,
    val manpage: Property<Boolean>,
)