package document.epub

import java.io.File

/**
 * Port of the EPUB validation domain (DOC-EPUBCHECK) — Gradle-free, pure.
 *
 * The adapter ([document.epub.LibEpubCheckAdapter]) bridges the real
 * `org.w3c:epubcheck` library; unit tests inject a plain fake. The port is
 * stateless: it must be reusable across calls (Ink Economy Law — a pure
 * deterministic function of the file).
 */
fun interface EpubCheckRunner {

    /**
     * Validates [file] (an EPUB binary) and returns the [EpubValidationResult].
     * Implementations must not throw for a non-conforming file — a failed
     * validation is an [EpubValidationResult.Invalid], not an exception.
     */
    fun validate(file: File): EpubValidationResult
}