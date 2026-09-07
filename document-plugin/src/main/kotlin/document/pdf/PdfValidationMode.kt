package document.pdf

/**
 * Severity mode of the PDF structural validation (DOC-PDF-CHECK) — mirror of
 * [document.epub.EpubValidationMode] (STRICT/LENIENT/OFF, default OFF for
 * backward compatibility).
 */
enum class PdfValidationMode {
    OFF,
    LENIENT,
    STRICT,
}