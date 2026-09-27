package document

import contracts.i18n.LanguageCatalog
import contracts.i18n.SupportedLanguage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-BOOK-MULTILANG — US-1: the pure book language plan domain.
 *
 * [BookLanguagePlanner] derives the target languages of a multi-language book
 * run from the N0 [LanguageCatalog] (decision D2/D3): explicit subset wins,
 * otherwise the whole catalog minus the source language; unknown codes are
 * ignored, duplicates collapsed (first occurrence), order deterministic. The
 * domain is Gradle-free and pure — a synthetic catalog pins the rules, and a
 * second test guards the real N0 catalog.
 */
class BookLanguagePlannerTest {

    private val catalog = listOf(
        SupportedLanguage(code = "en", name = "English", nativeName = "English"),
        SupportedLanguage(code = "fr", name = "French", nativeName = "Français"),
        SupportedLanguage(code = "de", name = "German", nativeName = "Deutsch"),
        SupportedLanguage(code = "ar", name = "Arabic", nativeName = "العربية", rtl = true),
    )

    @Test
    fun `translateToAll derives every catalog language except the source one`() {
        val plan = BookLanguagePlanner.plan("fr", emptyList(), translateToAll = true, catalog = catalog)

        assertEquals(listOf("en", "de", "ar"), plan.map { it.code })
    }

    @Test
    fun `translateToAll keeps the catalog order`() {
        val plan = BookLanguagePlanner.plan("en", emptyList(), translateToAll = true, catalog = catalog)

        assertEquals(listOf("fr", "de", "ar"), plan.map { it.code })
    }

    @Test
    fun `an explicit subset is used verbatim in the given order`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("de", "en"), translateToAll = false, catalog = catalog)

        assertEquals(listOf("de", "en"), plan.map { it.code })
    }

    @Test
    fun `the explicit subset excludes the source language`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("en", "fr", "de"), translateToAll = false, catalog = catalog)

        assertEquals(listOf("en", "de"), plan.map { it.code })
    }

    @Test
    fun `the explicit subset deduplicates keeping the first occurrence`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("de", "en", "de"), translateToAll = false, catalog = catalog)

        assertEquals(listOf("de", "en"), plan.map { it.code })
    }

    @Test
    fun `the explicit subset ignores unknown codes`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("en", "xx", "de"), translateToAll = false, catalog = catalog)

        assertEquals(listOf("en", "de"), plan.map { it.code })
    }

    @Test
    fun `an explicit subset takes precedence over translateToAll`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("de"), translateToAll = true, catalog = catalog)

        assertEquals(listOf("de"), plan.map { it.code })
    }

    @Test
    fun `no knob yields an empty plan`() {
        val plan = BookLanguagePlanner.plan("fr", emptyList(), translateToAll = false, catalog = catalog)

        assertTrue(plan.isEmpty(), "no language selected must be a no-op, got: ${plan.map { it.code }}")
    }

    @Test
    fun `a target carries the catalog metadata and derives the book file name`() {
        val plan = BookLanguagePlanner.plan("fr", listOf("ar"), translateToAll = false, catalog = catalog)
        val target = plan.single()

        assertEquals("Arabic", target.name)
        assertEquals("العربية", target.nativeName)
        assertTrue(target.rtl, "the RTL flag must be carried from the catalog")
        assertEquals("book-ar.adoc", target.fileName("book"))
        assertEquals("livre-ar.adoc", target.fileName("livre"))
    }

    @Test
    fun `the real N0 catalog yields the source language minus one target`() {
        val plan = BookLanguagePlanner.plan("fr", emptyList(), translateToAll = true)

        assertEquals(LanguageCatalog.ALL.size - 1, plan.size, "the whole catalog minus the source language")
        assertTrue("fr" !in plan.map { it.code }, "the source language must be excluded")
        assertTrue(plan.map { it.code }.containsAll(LanguageCatalog.supportedCodes() - "fr"))
    }
}
