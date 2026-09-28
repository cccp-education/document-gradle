package document

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
 * DOC-BOOK-VALIDATE-2 extends the block with the TOC file, the PDF directory
 * and the validation mode so `assembleBook` can validate the assembled book
 * against its table of contents:
 *
 * ```
 * document {
 *     book {
 *         pagesDir.set(file("src/book/pages"))
 *         tocFile.set(file("src/book/toc.adoc"))
 *         pdfsDir.set(file("src/book/pdfs"))
 *         validationMode.set(ValidationMode.STRICT)
 *     }
 * }
 * ```
 *
 * Concrete class with eagerly-initialised [Property]s for Kotlin DSL
 * access (pattern [DocumentEnrichDsl] / [DocumentOutputsDsl]).
 *
 * DOC-BOOK-TRANSLATE extends the block with the source / target languages:
 *
 * ```
 * document {
 *     book {
 *         pagesDir.set(file("src/book/pages"))
 *         tocFile.set(file("src/book/toc.adoc"))
 *         sourceLanguage.set("fr")   // default "fr"
 *         targetLanguage.set("en")   // blank (default) = no translation
 *     }
 * }
 * ```
 *
 * DOC-BOOK-MULTILANG adds the multi-language knobs:
 *
 * ```
 * document {
 *     book {
 *         translateToAll.set(true)                 // the whole LanguageCatalog − source
 *         targetLanguages.set(listOf("en", "de"))  // explicit subset (takes precedence)
 *     }
 * }
 * ```
 */
class BookDsl(
    val pagesDir: DirectoryProperty,
    val photosDir: DirectoryProperty,
    val title: Property<String>,
    val author: Property<String>,
    val tocFile: RegularFileProperty,
    val pdfsDir: DirectoryProperty,
    val validationMode: Property<ValidationMode>,
    /**
     * DOC-BOOK-MATTER — how the front/back matter policy is resolved from the
     * TOC. `DERIVED` (default) adopts the convention roots only when the TOC
     * declares them (no permanent false positive on a body-only book);
     * `LEGACY` keeps the historical `0`/`9` requirement.
     */
    val matterPolicy: Property<MatterPolicyMode>,
    /** DOC-BOOK-MATTER — emit hard page breaks at matter transitions (opt-in). */
    val matterBreaks: Property<Boolean>,
    /**
     * DOC-BOOK-CONSISTENCY-B6 — emit a previous / next cross-reference at the
     * foot of every emitted section. Off by default (backward compatible).
     */
    val navigation: Property<Boolean>,
    /**
     * DOC-BOOK-TRANSLATE — the language the scanned book is written in.
     * Defaults to `fr`; used only by `translateBook`.
     */
    val sourceLanguage: Property<String>,
    /**
     * DOC-BOOK-TRANSLATE — the language `translateBook` produces.
     * Blank (default) means "no translation": the task stays inactive.
     */
    val targetLanguage: Property<String>,
    /**
     * DOC-BOOK-MULTILANG — expand the multi-language plan to the whole
     * `LanguageCatalog` (N0) minus [sourceLanguage]. Off by default; used only
     * by `translateBookAllLanguages`.
     */
    val translateToAll: Property<Boolean>,
    /**
     * DOC-BOOK-MULTILANG — an explicit target-language subset for
     * `translateBookAllLanguages` (takes precedence over [translateToAll]).
     * Empty by default.
     */
    val targetLanguages: ListProperty<String>,
    /**
     * DOC-BOOK-PUBLISH — the output formats `publishBookAllLanguages` fans the
     * translated books into (lowercase names: `html`, `pdf`, `epub`, `docbook`,
     * `manpage`). Empty by default → no publication (Ink Economy Law).
     */
    val publishFormats: ListProperty<String>,
    /**
     * DOC-BOOK-PUBLISH (US-5) — also publish the assembled *source* book
     * (`book.adoc`) into each requested format, alongside the translated books.
     * Off by default (decision D8): the source book is already covered by the
     * mono-source converters.
     */
    val includeSource: Property<Boolean>,
)