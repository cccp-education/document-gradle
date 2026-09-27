package document

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * EPIC DOC-BOOK-PUBLISH — US-1: the pure book publication plan domain.
 *
 * [BookPublicationPlanner] derives the publication fan-out of a multi-language
 * book run — one `<base>-<lang>.<ext>` output per (translated language, format)
 * pair. The language source of truth is [BookLanguagePlanner] (N0
 * `LanguageCatalog`, decision D2); the format source of truth is
 * [DocumentFormat] (decision D3). The domain is Gradle-free and pure: unknown
 * format codes are ignored, duplicates collapsed (first occurrence), order
 * deterministic (language-major, then requested format order).
 */
class BookPublicationPlannerTest {

    private val languages = listOf(
        BookLanguageTarget(code = "en", name = "English", nativeName = "English", rtl = false),
        BookLanguageTarget(code = "de", name = "German", nativeName = "Deutsch", rtl = false),
    )

    @Test
    fun `no format yields an empty no-op plan`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = emptyList())

        assertTrue(plan.isEmpty(), "no format selected must be a no-op, got: ${plan.map { it.outputFileName }}")
    }

    @Test
    fun `no language yields an empty plan`() {
        val plan = BookPublicationPlanner.plan(emptyList(), baseName = "book", requestedFormats = listOf("html"))

        assertTrue(plan.isEmpty(), "no language selected must yield no output, got: ${plan.map { it.outputFileName }}")
    }

    @Test
    fun `each language is published in every requested format`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("html", "pdf"))

        assertEquals(
            listOf("book-en.html", "book-en.pdf", "book-de.html", "book-de.pdf"),
            plan.map { it.outputFileName },
        )
    }

    @Test
    fun `a target carries the source book, the output book and the resolved format`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("pdf"))
        val target = plan.single { it.language == "de" }

        assertEquals("de", target.language)
        assertEquals(DocumentFormat.PDF, target.format)
        assertEquals("book-de.adoc", target.sourceFileName)
        assertEquals("book-de.pdf", target.outputFileName)
    }

    @Test
    fun `the base name drives both the source and the output conventions`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "livre", requestedFormats = listOf("epub"))
        val target = plan.single { it.language == "en" }

        assertEquals("livre-en.adoc", target.sourceFileName)
        assertEquals("livre-en.epub", target.outputFileName)
    }

    @Test
    fun `an unknown format code is ignored`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("html", "xx", "pdf"))

        assertEquals(
            listOf("book-en.html", "book-en.pdf", "book-de.html", "book-de.pdf"),
            plan.map { it.outputFileName },
        )
    }

    @Test
    fun `a duplicated format is collapsed keeping the first occurrence`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("html", "pdf", "html"))
        val en = plan.filter { it.language == "en" }

        assertEquals(listOf("book-en.html", "book-en.pdf"), en.map { it.outputFileName })
    }

    @Test
    fun `format codes are case-insensitive`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("HTML", "Pdf"))

        assertEquals(listOf("book-en.html", "book-en.pdf", "book-de.html", "book-de.pdf"), plan.map { it.outputFileName })
    }

    @Test
    fun `the order is language-major then the requested format order`() {
        val plan = BookPublicationPlanner.plan(languages, baseName = "book", requestedFormats = listOf("pdf", "html"))

        assertEquals(
            listOf("book-en.pdf", "book-en.html", "book-de.pdf", "book-de.html"),
            plan.map { it.outputFileName },
        )
    }

    @Test
    fun `every document format is resolvable by its lowercase name`() {
        val names = DocumentFormat.ALL.map { it.name.lowercase() }
        val plan = BookPublicationPlanner.plan(
            languages.take(1),
            baseName = "book",
            requestedFormats = names,
        )

        assertEquals(DocumentFormat.ALL.size, plan.size, "every catalog format must be resolvable")
        assertEquals(names, plan.map { it.format.name.lowercase() })
        assertEquals(DocumentFormat.ALL.map { "book-en.${it.extension}" }, plan.map { it.outputFileName })
    }
}
