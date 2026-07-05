package document

import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/**
 * Nested DSL block `releaseNotes { }` (DOC-8) — configuration for the
 * release notes pipeline (git log → AsciiDoc/Markdown/JSON).
 *
 * ```
 * document {
 *     releaseNotes {
 *         fromTag.set("v1.0.0")
 *         toTag.set("HEAD")
 *         version.set("1.2.0")
 *         includeDownloads.set(true)
 *         rendererType.set("markdown")   // DOC-8.2 — asciidoc | markdown | json
 *         categories.set(mapOf(           // DOC-8.2 — custom category labels
 *             "feat" to "New features",
 *             "fix" to "Bug fixes",
 *             "custom" to "Custom category",
 *         ))
 *     }
 * }
 * ```
 *
 * Concrete class (not abstract) — initialised in the plugin via ObjectFactory
 * properties, mirroring the DOC-12 nested DSL pattern.
 *
 * DOC-8.2 — `rendererType` and `categories` are optional :
 *  - `rendererType` null → defaults to "asciidoc" in the generator.
 *  - `categories` empty → generator falls back to the [contracts.pipeline.ReleaseNotesConfig]
 *    default categories (7 Conventional Commit types with French labels).
 */
class ReleaseNotesDsl(
    val fromTag: Property<String>,
    val toTag: Property<String>,
    val version: Property<String>,
    val includeDownloads: Property<Boolean>,
    val rendererType: Property<String>,
    val categories: MapProperty<String, String>,
)