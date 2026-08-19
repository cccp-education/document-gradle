package document

import java.io.File

/**
 * Validates an assembled book against its table of contents.
 *
 * [BookValidator] is a pure object (no side effects, no Gradle dependencies)
 * that checks the consistency between:
 * - A directory of OCR-ed AsciiDoc pages (produced by codex-gradle)
 * - A list of [BookSection]s (the book's TOC — source of truth of the order)
 * - An optional directory of PDF files (the original scanned pages)
 *
 * Validation rules:
 * . *TOC coverage* — every TOC section has a corresponding AsciiDoc page
 *   (page number derived from the leading digits of the file name).
 * . *No orphan pages* — every AsciiDoc page has a corresponding TOC section.
 * . *No empty sections* — no AsciiDoc page is blank or whitespace-only.
 * . *No duplicate pages* — no two AsciiDoc pages share the same page number.
 * . *PDF resolution* — when [pdfsDir] is provided, every PDF referenced in the
 *   TOC must exist on disk.
 *
 * Ink Economy Law: the validation is deterministic and idempotent — running it
 * twice on the same inputs produces the same result. It never mutates any file.
 */
object BookValidator {

    private val LEADING_DIGITS = Regex("""^(\d+)""")

    /**
     * Validates the book represented by [pagesDir] against [toc].
     *
     * @param pagesDir the directory containing the OCR-ed AsciiDoc pages
     * @param toc the table of contents (list of [BookSection]s — source of truth)
     * @param pdfsDir optional directory of PDF files; when null, PDF
     *   references are not checked (backward compatibility)
     * @return a [BookValidationResult] — [BookValidationResult.Valid] if all
     *   checks pass, [BookValidationResult.Invalid] with reasons otherwise
     */
    fun validate(
        pagesDir: File,
        toc: List<BookSection>,
        pdfsDir: File? = null,
    ): BookValidationResult {
        val reasons = mutableListOf<String>()

        val adocFiles = loadAdocFiles(pagesDir)
        val pageNumbers = adocFiles.map { extractPageNumber(it.nameWithoutExtension) }
        val tocPages = toc.map { it.page }.toSet()

        // Rule 1: TOC coverage — every TOC section has a corresponding page
        for (section in toc) {
            if (section.page !in pageNumbers) {
                reasons.add("TOC section ${section.ref} '${section.title}' references page ${section.page} but no .adoc page found")
            }
        }

        // Rule 2: No orphan pages — every page has a corresponding TOC section
        for (pageNumber in pageNumbers) {
            if (pageNumber !in tocPages && pageNumber != Int.MAX_VALUE) {
                val fileName = adocFiles.find { extractPageNumber(it.nameWithoutExtension) == pageNumber }?.name ?: "unknown"
                reasons.add("Page $pageNumber ('$fileName') has no corresponding TOC section")
            }
        }

        // Rule 3: No empty sections — no blank or whitespace-only page
        for (file in adocFiles) {
            val text = file.readText().trim()
            if (text.isEmpty()) {
                reasons.add("Page '${file.name}' is empty (blank or whitespace-only content)")
            }
        }

        // Rule 4: No duplicate pages — no two files share the same page number
        val pageNumberCounts = pageNumbers.groupBy { it }.mapValues { it.value.size }
        for ((pageNumber, count) in pageNumberCounts) {
            if (count > 1 && pageNumber != Int.MAX_VALUE) {
                reasons.add("Duplicate page: $pageNumber appears $count times in the pages directory")
            }
        }

        // Rule 5: PDF resolution — when pdfsDir is provided, every referenced PDF must exist
        if (pdfsDir != null && pdfsDir.isDirectory) {
            for (section in toc) {
                val pdfFile = File(pdfsDir, section.pdfFile)
                if (!pdfFile.exists()) {
                    reasons.add("TOC section ${section.ref} references PDF '${section.pdfFile}' but file not found in ${pdfsDir.name}")
                }
            }
        }

        return if (reasons.isEmpty()) {
            BookValidationResult.Valid(pageCount = adocFiles.size)
        } else {
            BookValidationResult.Invalid(reasons = reasons)
        }
    }

    private fun loadAdocFiles(pagesDir: File): List<File> {
        if (!pagesDir.exists() || !pagesDir.isDirectory) return emptyList()
        return pagesDir.listFiles { f -> f.isFile && f.extension.equals("adoc", ignoreCase = true) }
            .orEmpty()
            .toList()
    }

    private fun extractPageNumber(nameWithoutExtension: String): Int {
        val match = LEADING_DIGITS.find(nameWithoutExtension)
        return if (match != null) {
            match.groupValues[1].toInt()
        } else {
            Int.MAX_VALUE
        }
    }
}