package document

import org.gradle.api.provider.Property

/**
 * Nested DSL block `releaseNotes { }` (DOC-8) — configuration for the
 * release notes pipeline (git log → AsciiDoc).
 *
 * ```
 * document {
 *     releaseNotes {
 *         fromTag.set("v1.0.0")
 *         toTag.set("HEAD")
 *         version.set("1.2.0")
 *         includeDownloads.set(true)
 *     }
 * }
 * ```
 *
 * Concrete class (not abstract) — initialised in the plugin via ObjectFactory
 * properties, mirroring the DOC-12 nested DSL pattern.
 */
class ReleaseNotesDsl(
    val fromTag: Property<String>,
    val toTag: Property<String>,
    val version: Property<String>,
    val includeDownloads: Property<Boolean>,
)