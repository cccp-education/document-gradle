package document

import org.asciidoctor.SafeMode
import org.gradle.api.provider.Property
import document.security.IncludeGuardMode

/**
 * Nested DSL block `converter { }` (DOC-CR3-2).
 *
 * Configures the AsciidoctorJ safe-mode guard applied to every conversion
 * (HTML/PDF/EPUB/DocBook/ManPage). SafeMode controls how AsciidoctorJ
 * accesses the filesystem (includes, images, stylesheets) during conversion:
 *
 * - [SafeMode.UNSAFE]  : full filesystem access (default, backward-compatible)
 * - [SafeMode.SERVER]  : restrictive, but allows includes from the doc dir
 * - [SafeMode.SECURE]  : locked down, no filesystem access outside the doc
 *
 * The value is threaded into [document.DocumentConverter.buildOptions] and
 * therefore governs every [document.ConvertDocumentTask] execution.
 */
class ConverterDsl(
    val safeMode: Property<SafeMode>,
    val includeGuard: Property<IncludeGuardMode>,
)
