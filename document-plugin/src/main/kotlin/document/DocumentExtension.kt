package document

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property

/**
 * Extension Gradle `document { }` — point d'entree du DSL documentaire.
 *
 * Pattern : abstract class + Property<T> lazy (pattern planner/codebase).
 * Ordre de precedent : CLI (-P) > DSL (block document { }) > convention (defaut).
 *
 * Usage DSL :
 * ```
 * document {
 *     source.set(file("src/docs/livre.adoc"))
 *     outputDir.set(layout.buildDirectory.dir("docs/document"))
 *     formats.set(listOf(DocumentFormat.HTML, DocumentFormat.PDF))
 *     enrichPlantUml.set(true)
 *     theme {
 *         pdfTheme.set(file("talaria-theme.yml"))
 *         htmlStylesheet.set(file("talaria.css"))
 *         epubStylesheet.set(file("epub.css"))
 *         logo.set(file("logo.png"))
 *     }
 * }
 * ```
 *
 * Usage CLI (priorite max) :
 * ```
 * ./gradlew generateDocument -Pdocument.source=src/docs/livre.adoc
 * ./gradlew convertDocumentToPdf -Pdocument.pdfTheme=theme.yml
 * ./gradlew enrichDocument -Pdocument.enrichPlantUml=true
 * ```
 */
abstract class DocumentExtension {

    abstract val source: RegularFileProperty
    abstract val outputDir: DirectoryProperty
    abstract val formats: ListProperty<DocumentFormat>
    abstract val pdfTheme: RegularFileProperty
    abstract val htmlStylesheet: RegularFileProperty
    abstract val enrichPlantUml: Property<Boolean>
    abstract val enrichImages: Property<Boolean>
    abstract val enrichPassthrough: Property<Boolean>

    /** Prompt de generation IA (DOC-2). Si set, generateDocument invoque le LLM. */
    abstract val prompt: Property<String>

    /** Mode LLM : "ollama" (defaut, production) ou "fake" (tests, sans reseau). */
    abstract val llmMode: Property<String>

    /** System prompt optionnel (conventions AsciiDoc du workspace). */
    abstract val systemPrompt: Property<String>

    /** Theme visuel (DOC-10) — feuille de style EPUB. */
    abstract val epubStylesheet: RegularFileProperty

    /** Theme visuel (DOC-10) — image logo pour le PDF (cover image). */
    abstract val logo: RegularFileProperty

    /**
     * Nested DSL block `theme { }` for visual theming (DOC-10).
     *
     * Usage:
     * ```
     * document {
     *     theme {
     *         pdfTheme.set(file("talaria-theme.yml"))
     *         htmlStylesheet.set(file("talaria.css"))
     *         epubStylesheet.set(file("epub.css"))
     *         logo.set(file("logo.png"))
     *     }
     * }
     * ```
     */
    fun theme(action: Action) {
        action.execute(this)
    }

    fun interface Action {
        fun execute(ext: DocumentExtension)
    }

    fun formats(vararg formats: DocumentFormat) {
        this.formats.set(formats.toList())
    }
}