package document

import document.batch.BatchDsl
import document.template.TemplateDsl
import document.translation.TranslationDsl
import org.gradle.api.Action
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Nested

/**
 * Extension Gradle `document { }` — point d'entree du DSL documentaire (DOC-12 unified).
 *
 * Pattern : abstract class + Property<T> lazy (pattern planner/codebase).
 * Ordre de precedent : CLI (-P) > DSL (block document { }) > convention (defaut).
 *
 * DOC-12 — Unified DSL adds three nested blocks on top of the legacy flat
 * properties (kept for backward compatibility with DOC-9/DOC-10/DOC-11 wiring):
 *
 * ```
 * document {
 *     source.set(file("src/docs/livre.adoc"))
 *     enrich {
 *         plantuml.set(true)
 *         images.set(true)
 *         passthrough.set(true)
 *     }
 *     outputs {
 *         html.set(true)
 *         pdf.set(true)
 *         epub.set(true)
 *     }
 *     theme {
 *         pdfTheme.set(file("theme.yml"))
 *         htmlStylesheet.set(file("style.css"))
 *     }
 *     metadata {
 *         title.set("Mon Livre")
 *         author.set("Auteur")
 *         language.set("fr")
 *     }
 * }
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

    /** Book pipeline (DOC-11) — directory of OCR-ed AsciiDoc pages. */
    abstract val bookPagesDir: DirectoryProperty

    /** Book pipeline (DOC-11) — directory of original page photos. */
    abstract val bookPhotosDir: DirectoryProperty

    /** Book pipeline (DOC-11) — book title. */
    abstract val bookTitle: Property<String>

    /** Book pipeline (DOC-11) — book author. */
    abstract val bookAuthor: Property<String>

    /**
     * Nested DSL block `enrich { }` (DOC-12). Concrete val initialised in the
     * plugin registration via the [org.gradle.api.model.ObjectFactory]. The
     * concrete [DocumentEnrichDsl] exposes its properties as `val` so the
     * Kotlin DSL resolves `plantuml`, `images`, `passthrough` inside the
     * block without managed-type accessor generation.
     */
    lateinit var enrich: DocumentEnrichDsl
        private set

    /**
     * Nested DSL block `outputs { }` (DOC-12).
     */
    lateinit var outputs: DocumentOutputsDsl
        private set

    /**
     * Nested DSL block `metadata { }` (DOC-12).
     */
    lateinit var metadata: DocumentMetadataDsl
        private set

    /**
     * Nested DSL block `releaseNotes { }` (DOC-8) — git log → AsciiDoc.
     */
    lateinit var releaseNotes: ReleaseNotesDsl
        private set

    /**
     * Nested DSL block `book { }` (DOC-12 extension — book pipeline DOC-11).
     */
    lateinit var book: BookDsl
        private set

    /**
     * Nested DSL block `template { }` (DOC-13 — AsciiDoc template substitution).
     */
    lateinit var template: TemplateDsl
        private set

    lateinit var batch: BatchDsl
        private set

    lateinit var translation: TranslationDsl
        private set

    internal fun initNested(
        enrich: DocumentEnrichDsl,
        outputs: DocumentOutputsDsl,
        metadata: DocumentMetadataDsl,
        releaseNotes: ReleaseNotesDsl,
        book: BookDsl,
        template: TemplateDsl,
        batch: BatchDsl,
        translation: TranslationDsl,
    ) {
        this.enrich = enrich
        this.outputs = outputs
        this.metadata = metadata
        this.releaseNotes = releaseNotes
        this.book = book
        this.template = template
        this.batch = batch
        this.translation = translation
    }

    /**
     * Nested DSL block `theme { }` for visual theming (DOC-10).
     */
    fun theme(action: Action<DocumentExtension>) {
        action.execute(this)
    }

    /**
     * Nested DSL block `enrich { }` (DOC-12).
     */
    fun enrich(action: Action<DocumentEnrichDsl>) {
        action.execute(enrich)
    }

    /**
     * Nested DSL block `outputs { }` (DOC-12).
     */
    fun outputs(action: Action<DocumentOutputsDsl>) {
        action.execute(outputs)
    }

    /**
     * Nested DSL block `metadata { }` (DOC-12).
     */
    fun metadata(action: Action<DocumentMetadataDsl>) {
        action.execute(metadata)
    }

    /**
     * Nested DSL block `releaseNotes { }` (DOC-8).
     */
    fun releaseNotes(action: Action<ReleaseNotesDsl>) {
        action.execute(releaseNotes)
    }

    /**
     * Nested DSL block `book { }` (DOC-12 extension — book pipeline DOC-11).
     */
    fun book(action: Action<BookDsl>) {
        action.execute(book)
    }

    /**
     * Nested DSL block `template { }` (DOC-13 — AsciiDoc template substitution).
     */
    fun template(action: Action<TemplateDsl>) {
        action.execute(template)
    }

    fun batch(action: Action<BatchDsl>) {
        action.execute(batch)
    }

    fun translation(action: Action<TranslationDsl>) {
        action.execute(translation)
    }

    fun formats(vararg formats: DocumentFormat) {
        this.formats.set(formats.toList())
    }
}