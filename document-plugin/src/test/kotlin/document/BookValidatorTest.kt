package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Nested
import java.io.File
import java.nio.file.Files

class BookValidatorTest {

    private fun tempDir(): File = Files.createTempDirectory("doc-book-validate").toFile()

    private fun page(dir: File, name: String, content: String): File {
        return File(dir, name).apply { writeText(content) }
    }

    @Nested
    inner class ValidBook {

        @Test
        fun `validator accepts a complete book with all pages present and non-empty`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Devenir Formateur\n\nIntro content.")
            page(pagesDir, "006-page.adoc", "== Historique\n\nHistory content.")
            page(pagesDir, "014-page.adoc", "== Competences\n\nSkills content.")

            val toc = listOf(
                BookSection("1.0.1", "Devenir Formateur", 5, "005.pdf"),
                BookSection("1.0.2", "Historique", 6, "006.pdf"),
                BookSection("1.0.3", "Competences", 14, "014.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Valid, "a complete book must be valid")
        }

        @Test
        fun `valid result carries page count`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            page(pagesDir, "006-page.adoc", "== Section 2\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
                BookSection("1.0.2", "Section 2", 6, "006.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc) as BookValidationResult.Valid

            assertEquals(2, result.pageCount, "page count must match the number of pages")
        }
    }

    @Nested
    inner class MissingPages {

        @Test
        fun `validator detects a page referenced in TOC but missing from pages dir`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Devenir Formateur\n\nIntro.")

            val toc = listOf(
                BookSection("1.0.1", "Devenir Formateur", 5, "005.pdf"),
                BookSection("1.0.2", "Historique", 14, "014.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Invalid, "missing page must be invalid")
            val invalid = result as BookValidationResult.Invalid
            assertTrue(invalid.reasons.any { it.contains("14") }, "reason must mention missing page 14")
        }

        @Test
        fun `invalid result carries all missing page numbers`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
                BookSection("1.0.2", "Section 2", 14, "014.pdf"),
                BookSection("1.0.3", "Section 3", 15, "015.pdf"),
            )

            val invalid = BookValidator.validate(pagesDir, toc) as BookValidationResult.Invalid

            assertTrue(invalid.reasons.any { it.contains("14") }, "reason must mention page 14")
            assertTrue(invalid.reasons.any { it.contains("15") }, "reason must mention page 15")
        }
    }

    @Nested
    inner class EmptySections {

        @Test
        fun `validator detects an empty page file`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "")
            page(pagesDir, "006-page.adoc", "== Section 2\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
                BookSection("1.0.2", "Section 2", 6, "006.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Invalid, "empty page must be invalid")
            val invalid = result as BookValidationResult.Invalid
            assertTrue(invalid.reasons.any { it.contains("empty") || it.contains("vide") || it.contains("blank") },
                "reason must mention empty page")
        }

        @Test
        fun `validator detects a page file with only whitespace`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "   \n\n  \n")
            page(pagesDir, "006-page.adoc", "== Section 2\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
                BookSection("1.0.2", "Section 2", 6, "006.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Invalid, "whitespace-only page must be invalid")
        }
    }

    @Nested
    inner class TocMismatch {

        @Test
        fun `validator detects a page present but not referenced in TOC`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            page(pagesDir, "006-page.adoc", "== Orphan page\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Invalid, "orphan page must be detected")
            val invalid = result as BookValidationResult.Invalid
            assertTrue(invalid.reasons.any { it.contains("006") || it.contains("6") },
                "reason must mention the orphan page")
        }
    }

    @Nested
    inner class PdfReferences {

        @Test
        fun `validator detects a PDF reference in TOC that does not exist`() {
            val pagesDir = tempDir()
            val pdfsDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc, pdfsDir = pdfsDir)

            assertTrue(result is BookValidationResult.Invalid, "missing PDF ref must be invalid when pdfsDir is provided")
        }

        @Test
        fun `validator skips PDF resolution when pdfsDir is null`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc, pdfsDir = null)

            assertTrue(result is BookValidationResult.Valid, "when pdfsDir is null, PDF refs are not checked")
        }

        @Test
        fun `validator accepts when all referenced PDFs exist`() {
            val pagesDir = tempDir()
            val pdfsDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            File(pdfsDir, "005.pdf").writeBytes(byteArrayOf(0x25, 0x50))

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc, pdfsDir = pdfsDir)

            assertTrue(result is BookValidationResult.Valid, "existing PDFs must pass validation")
        }

        @Test
        fun `validator detects missing PDF when pdfsDir is provided`() {
            val pagesDir = tempDir()
            val pdfsDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            // 005.pdf is NOT created

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc, pdfsDir = pdfsDir)

            assertTrue(result is BookValidationResult.Invalid, "missing PDF must be detected")
            val invalid = result as BookValidationResult.Invalid
            assertTrue(invalid.reasons.any { it.contains("005.pdf") }, "reason must mention the missing PDF")
        }
    }

    @Nested
    inner class PageOrdering {

        @Test
        fun `validator detects duplicate page numbers`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            page(pagesDir, "005-dup.adoc", "== Duplicate\n\nContent.")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Invalid, "duplicate page must be invalid")
            val invalid = result as BookValidationResult.Invalid
            assertTrue(invalid.reasons.any { it.contains("duplicate", ignoreCase = true) || it.contains("dupliqu", ignoreCase = true) },
                "reason must mention duplicate")
        }
    }

    @Nested
    inner class EdgeCases {

        @Test
        fun `validator accepts empty pages dir with empty TOC`() {
            val pagesDir = tempDir()

            val result = BookValidator.validate(pagesDir, emptyList())

            assertTrue(result is BookValidationResult.Valid, "empty book with empty TOC is valid")
        }

        @Test
        fun `invalid result has at least one reason`() {
            val pagesDir = tempDir()

            val toc = listOf(
                BookSection("1.0.1", "Missing", 99, "099.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc) as BookValidationResult.Invalid

            assertTrue(result.reasons.isNotEmpty(), "invalid result must have reasons")
            assertNotNull(result.reasons.joinToString(), "reasons must be joinable to a string")
        }

        @Test
        fun `validator ignores non-adoc files when checking for orphan pages`() {
            val pagesDir = tempDir()
            page(pagesDir, "005-page.adoc", "== Section 1\n\nContent.")
            File(pagesDir, "notes.txt").writeText("not a page")

            val toc = listOf(
                BookSection("1.0.1", "Section 1", 5, "005.pdf"),
            )

            val result = BookValidator.validate(pagesDir, toc)

            assertTrue(result is BookValidationResult.Valid, "non-adoc files must not count as pages")
        }
    }
}