package document

import contracts.i18n.LanguageCatalog
import contracts.i18n.SupportedLanguage

/**
 * A single target language of a multi-language book run (EPIC
 * DOC-BOOK-MULTILANG, US-1).
 *
 * Carries the resolved N0 [SupportedLanguage] metadata (name, native name, RTL
 * flag, locale tag) alongside the derived output file name, so the task layer
 * never recomputes the naming convention. The [rtl] flag is *informational* in
 * this EPIC — RTL rendering is a distinct follow-up (decision D8).
 */
data class BookLanguageTarget(
    val code: String,
    val name: String,
    val nativeName: String,
    val rtl: Boolean,
) {

    /** `<baseName>-<code>.adoc` — mirrors the mono-language `translateBook` naming. */
    fun fileName(baseName: String): String = "$baseName-$code.adoc"

    companion object {
        fun of(language: SupportedLanguage): BookLanguageTarget =
            BookLanguageTarget(
                code = language.code,
                name = language.name,
                nativeName = language.nativeName,
                rtl = language.rtl,
            )
    }
}

/**
 * Stateless planner of the target languages of a multi-language book run
 * (EPIC DOC-BOOK-MULTILANG, US-1 — decisions D2/D3/D4).
 *
 * The plan is *derived* from the N0 [LanguageCatalog] (single source of truth,
 * no local catalogue): an explicit `requested` subset wins when non-empty,
 * otherwise `translateToAll` expands to the whole catalog minus the source
 * language. Unknown codes are ignored, duplicates collapsed (first occurrence),
 * and the catalog order is preserved — the plan is deterministic and pure.
 *
 * ```
 * BookLanguagePlanner.plan("fr", listOf("en", "de"), translateToAll = false)
 * BookLanguagePlanner.plan("fr", emptyList(), translateToAll = true)  // whole catalog − fr
 * ```
 *
 * The domain is Gradle-free; the N0 catalogue is injectable so unit tests can
 * pin the rules against a synthetic catalog.
 */
object BookLanguagePlanner {

    /**
     * @param sourceLanguage the language the book is written in — always excluded
     * @param requested explicit target codes (may be empty)
     * @param translateToAll when `true` and [requested] is empty, expand to the
     *   whole catalog minus [sourceLanguage]
     * @param catalog the supported-language catalogue (defaults to the N0
     *   [LanguageCatalog]); injectable for tests
     */
    fun plan(
        sourceLanguage: String,
        requested: Collection<String>,
        translateToAll: Boolean,
        catalog: List<SupportedLanguage> = LanguageCatalog.ALL,
    ): List<BookLanguageTarget> {
        val byCode = catalog.associateBy { it.code }

        val selection: List<SupportedLanguage> = when {
            requested.isNotEmpty() -> requested.mapNotNull { byCode[it] }
            translateToAll -> catalog
            else -> emptyList()
        }

        return selection
            .filter { it.code != sourceLanguage }
            .distinctBy { it.code }
            .map { BookLanguageTarget.of(it) }
    }
}
