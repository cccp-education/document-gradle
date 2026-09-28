package document

/**
 * A single publication unit of a multi-language book run (EPIC
 * DOC-BOOK-PUBLISH, US-1).
 *
 * Pairs one translated book ([language]) with one output [format], carrying the
 * fully-resolved [sourceFileName] (`<base>-<lang>.adoc`, consumed as source) and
 * [outputFileName] (`<base>-<lang>.<ext>`, the published book), so the task
 * layer never recomputes either naming convention.
 */
data class BookPublicationTarget(
    val language: String,
    val format: DocumentFormat,
    val sourceFileName: String,
    val outputFileName: String,
    /**
     * DOC-BOOK-PUBLISH (US-5) — `true` when this target publishes the assembled
     * *source* book (`book.adoc`) rather than a translated book (`book-<lang>.adoc`).
     * The source target is emitted first (decision D8).
     */
    val isSource: Boolean = false,
)

/**
 * Stateless planner of the publication fan-out of a multi-language book run
 * (EPIC DOC-BOOK-PUBLISH, US-1 — decisions D2/D3/D4).
 *
 * The plan is the cross product of the translated languages (source of truth:
 * the [BookLanguageTarget] list already resolved by [BookLanguagePlanner] from
 * the N0 `LanguageCatalog`) and the requested output formats (source of truth:
 * [DocumentFormat]). Unknown format codes are ignored, duplicates collapsed
 * (first occurrence), blank language codes dropped (a `book-.html` ghost is
 * never produced), and the order is deterministic — *language-major, then
 * the requested format order* — so the fan-out is reproducible.
 *
 * ```
 * BookPublicationPlanner.plan(targets, "book", listOf("html", "pdf"))
 * // book-en.html, book-en.pdf, book-de.html, book-de.pdf
 * ```
 *
 * The domain is Gradle-free and pure: it derives names and pairs only, never
 * touching the filesystem nor AsciidoctorJ (the task layer converts).
 */
object BookPublicationPlanner {

    /**
     * @param languages the translated languages of the book (already resolved,
     *   source language excluded); each yields `<base>-<code>.adoc`
     * @param baseName the base name shared by the source and output books
     *   (default `book`)
     * @param requestedFormats the requested output formats by lowercase name
     *   (`html`, `pdf`, `epub`, `docbook`, `manpage`); empty means no fan-out
     */
    fun plan(
        languages: List<BookLanguageTarget>,
        baseName: String,
        requestedFormats: Collection<String>,
        sourceLanguage: String = "",
        includeSource: Boolean = false,
    ): List<BookPublicationTarget> {
        val formats = requestedFormats
            .mapNotNull { name -> DocumentFormat.ALL.firstOrNull { it.name.equals(name, ignoreCase = true) } }
            .distinct()

        if (formats.isEmpty()) return emptyList()

        // DOC-BOOK-PUBLISH (US-5) — the assembled source book is opt-in and
        // emitted first: `<base>.adoc` -> `<base>.<ext>` (no language suffix).
        val sourceTargets = if (includeSource) {
            formats.map { format ->
                BookPublicationTarget(
                    language = sourceLanguage,
                    format = format,
                    sourceFileName = "$baseName.adoc",
                    outputFileName = "$baseName.${format.extension}",
                    isSource = true,
                )
            }
        } else {
            emptyList()
        }

        val translatedTargets = languages
            .filter { it.code.isNotBlank() }
            .flatMap { language ->
                formats.map { format ->
                    BookPublicationTarget(
                        language = language.code,
                        format = format,
                        sourceFileName = "$baseName-${language.code}.adoc",
                        outputFileName = "$baseName-${language.code}.${format.extension}",
                    )
                }
            }

        return sourceTargets + translatedTargets
    }
}
