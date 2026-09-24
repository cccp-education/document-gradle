package document

import java.io.File

/**
 * Renders one OCR-ed page file into the book body, applying the image rules of
 * EPIC DOC-BOOK-IMAGES (code-review S-258 B4/B5):
 *
 * - *ghost repair* (always) — an `image::` directive whose target file does not
 *   exist is substituted with the page's own scan (or dropped when the page has
 *   no scan), so the assembled book stays epubcheck-valid (RSC-007);
 * - *illustration* (opt-in) — when [illustrate] is set, the page scan is emitted
 *   as an `image::` directive so the book is illustrated by the photographed
 *   pages (never by default: the whole scanned corpus is hundreds of megabytes).
 *
 * Shared by both page-naming conventions (scanned `NNN.adoc` and codex
 * `%03d-*.adoc`) so illustration and repair behave identically regardless of
 * the corpus layout.
 *
 * Ink Economy Law: pure, deterministic; without any ghost and without
 * illustration the content is returned byte-identical (no-op).
 */
object BookPageRenderer {

    /**
     * @param file the OCR-ed AsciiDoc page
     * @param imageDir the directory scans are resolved against
     * @param illustrate whether the page scan is emitted as an illustration
     */
    fun render(file: File, imageDir: File, illustrate: Boolean): String {
        val scan = BookImageResolver.scanForBase(file.nameWithoutExtension, imageDir)
        val repaired = BookImageRewriter.repair(file.readText().trim(), imageDir, scan)
        if (!illustrate || scan == null) return repaired
        // The repaired text may already reference the scan (a ghost substituted
        // to the very same file); never emit it twice.
        if (scan.name in repaired) return repaired
        return repaired + "\n\n" + "image::${scan.name}[]"
    }
}
