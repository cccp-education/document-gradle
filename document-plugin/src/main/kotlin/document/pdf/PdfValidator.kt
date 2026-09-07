package document.pdf

import java.io.File

/**
 * Port of the PDF validation domain (DOC-PDF-CHECK) — Gradle-free, pure.
 * Mirror of [document.epub.EpubCheckRunner].
 *
 * The adapter ([document.pdf.PdfBoxValidatorAdapter]) bridges the real
 * Apache PDFBox library; unit tests inject a plain fake. The port is
 * stateless: it must be reusable across calls (Ink Economy Law — a pure
 * deterministic function of the file).
 */
fun interface PdfValidator {

    /**
     * Validates [file] (a PDF binary) and returns the [PdfValidationResult].
     * Implementations must not throw for a non-conforming file — a failed
     * validation is a [PdfValidationResult.Invalid], not an exception.
     */
    fun validate(file: File): PdfValidationResult
}