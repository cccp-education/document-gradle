package document

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for the structural validation of a book table of contents
 * (DOC-BOOK-DOMAIN-6).
 *
 * [BookValidator.validateStructure] is the tree-level counterpart of
 * [BookValidator.validate] (file-level): it checks the *structure* of the
 * section list itself, without any I/O —
 *
 * . *ref continuity* — every multi-segment `ref` has its parent `ref`
 *   (the prefix truncated to the last segment) present in the TOC, so the
 *   assembled tree never synthesizes hidden ancestors (e.g. no jump `1` →
 *   `1.1.1` without an intermediate `1.1`);
 * . *ref uniqueness* — no two sections share the same (`ref`, `page`) pair;
 *   the same `ref` with distinct pages is legitimate (multi-page sections
 *   expanded by [BookTocParser]);
 * . *matter completeness* — the book carries at least one FRONT section
 *   (`0.x`) and one BACK section (`9.x`);
 * . *page order monotonicity* — physical pages never regress in document
 *   order.
 *
 * Ink Economy Law: the validation is a pure function of the sections —
 * no I/O, no Gradle dependency, fully deterministic.
 */
class BookValidatorStructureTest {

    private fun validSections() = listOf(
        BookSection(ref = "0", title = "Front Matter", page = 1, pdfFile = "000.adoc"),
        BookSection(ref = "0.1", title = "Preface", page = 2, pdfFile = "001.adoc"),
        BookSection(ref = "1", title = "Part I", page = 3, pdfFile = "002.adoc"),
        BookSection(ref = "1.1", title = "Chapter 1", page = 4, pdfFile = "003.adoc"),
        BookSection(ref = "1.2", title = "Chapter 2", page = 5, pdfFile = "004.adoc"),
        BookSection(ref = "9", title = "Back Matter", page = 6, pdfFile = "005.adoc"),
        BookSection(ref = "9.1", title = "Glossary", page = 7, pdfFile = "006.adoc"),
    )

    @Test
    fun `a complete well ordered structure is valid`() {
        val result = BookValidator.validateStructure(validSections())

        assertTrue(result is BookValidationResult.Valid, "a complete structure must be valid")
        assertEquals(7, (result as BookValidationResult.Valid).pageCount)
    }

    @Test
    fun `a child ref whose parent ref is missing breaks ref continuity`() {
        val sections = listOf(
            BookSection(ref = "1", title = "Part I", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1.1.1", title = "Jumped section", page = 2, pdfFile = "002.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Invalid, "a ref jump must be invalid")
        assertTrue(
            (result as BookValidationResult.Invalid).reasons.any { it.contains("1.1") },
            "the missing parent ref must be reported, got: ${result.reasons}",
        )
    }

    @Test
    fun `top level refs need no parent`() {
        val sections = listOf(
            BookSection(ref = "1", title = "Part I", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "2", title = "Part II", page = 2, pdfFile = "002.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(
            result is BookValidationResult.Invalid,
            "top-level refs are structurally continuous but the book lacks FRONT/BACK matter",
        )
        assertTrue(
            (result as BookValidationResult.Invalid).reasons.none { it.contains("continuity") },
            "no continuity finding must be raised for top-level refs, got: ${result.reasons}",
        )
    }

    @Test
    fun `two sections sharing the same ref and page are flagged as duplicates`() {
        val sections = listOf(
            BookSection(ref = "0.1", title = "Preface", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "9.1", title = "Glossary", page = 3, pdfFile = "003.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Invalid, "a duplicated (ref, page) pair must be invalid")
        assertTrue(
            (result as BookValidationResult.Invalid).reasons.any { it.contains("duplicate", ignoreCase = true) },
            "the duplicate must be reported, got: ${result.reasons}",
        )
    }

    @Test
    fun `a multi page section sharing its ref across distinct pages is not a duplicate`() {
        val sections = listOf(
            BookSection(ref = "0", title = "Front Matter", page = 1, pdfFile = "000.adoc"),
            BookSection(ref = "0.1", title = "Preface", page = 2, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Chapter", page = 3, pdfFile = "002.adoc"),
            BookSection(ref = "1.1", title = "Section", page = 4, pdfFile = "003.adoc"),
            BookSection(ref = "1.1", title = "Section", page = 5, pdfFile = "004.adoc"),
            BookSection(ref = "9", title = "Back Matter", page = 6, pdfFile = "005.adoc"),
            BookSection(ref = "9.1", title = "Glossary", page = 7, pdfFile = "006.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Valid, "multi-page sections must stay valid, got: $result")
        assertEquals(7, (result as BookValidationResult.Valid).pageCount)
    }

    @Test
    fun `a page order regression is flagged`() {
        val sections = listOf(
            BookSection(ref = "0.1", title = "Preface", page = 3, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
            BookSection(ref = "9.1", title = "Glossary", page = 4, pdfFile = "003.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Invalid, "a page regression must be invalid")
        assertTrue(
            (result as BookValidationResult.Invalid).reasons.any { it.contains("page order", ignoreCase = true) },
            "the page regression must be reported, got: ${result.reasons}",
        )
    }

    @Test
    fun `a structure without back matter is flagged`() {
        val sections = listOf(
            BookSection(ref = "0.1", title = "Preface", page = 1, pdfFile = "001.adoc"),
            BookSection(ref = "1", title = "Part I", page = 2, pdfFile = "002.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Invalid, "a book without BACK matter must be invalid")
        assertTrue(
            (result as BookValidationResult.Invalid).reasons.any { it.contains("BACK", ignoreCase = true) },
            "the missing BACK matter must be reported, got: ${result.reasons}",
        )
    }

    @Test
    fun `every finding is reported at once`() {
        val sections = listOf(
            BookSection(ref = "1.1.1", title = "Jumped section", page = 5, pdfFile = "001.adoc"),
            BookSection(ref = "1.1.1", title = "Jumped section", page = 5, pdfFile = "001.adoc"),
            BookSection(ref = "1.1.1", title = "Jumped section", page = 2, pdfFile = "002.adoc"),
        )

        val result = BookValidator.validateStructure(sections)

        assertTrue(result is BookValidationResult.Invalid)
        val reasons = (result as BookValidationResult.Invalid).reasons
        assertTrue(reasons.size >= 3, "continuity, duplicate and page-order findings must all be reported, got: $reasons")
    }
}
