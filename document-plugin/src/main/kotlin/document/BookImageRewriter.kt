package document

import java.io.File

/**
 * Repairs the ghost `image::` directives produced by OCR.
 *
 * When the vision model recognises a picture caption but cannot materialise the
 * picture itself, it emits an `image::` directive whose target file does not
 * exist next to the page (e.g. `image::qr-code.png[]` on page 157, while the
 * page scan is `157.jpg`). Such a dangling reference makes the EPUB fail
 * epubcheck (RSC-007).
 *
 * The rewriter resolves each ghost against the page's own scan:
 * - target file exists → the line is kept unchanged;
 * - target missing, page scan exists → the target is replaced by the page scan
 *   (block `image::` and inline `image:` forms, legend and trailing comment
 *   preserved), so the figure is illustrated by the photographed page;
 * - target missing, no page scan → the directive line is dropped, so the
 *   EPUB stays conformant rather than referencing a missing resource.
 *
 * The doubt itself is not erased from the source: [BookOcrFailureDetector]
 * still reports the IMAGE_MISSING issue for human iteration. This object only
 * makes the *assembled* book self-consistent (Rule 7 — the source is never
 * mutated).
 *
 * Ink Economy Law: pure, deterministic; without any ghost the content is
 * returned byte-identical (no-op).
 */
object BookImageRewriter {

    // Covers both the block macro `image::target[]` and the inline macro
    // `image:target[]` (the inline form is used by the real corpus, S-252).
    private val IMAGE_DIRECTIVE = Regex("""image::?([^\[\s]+)\[""")

    /**
     * Repairs every ghost image directive of [text].
     *
     * @param text the OCR-ed AsciiDoc content of a page
     * @param dir the directory the directives are resolved against (the page
     *   directory)
     * @param pageScan the scan of the owning page, used to substitute a ghost
     *   (`null` when the page has no scan → ghosts are dropped)
     * @return the repaired content; byte-identical to [text] when no ghost
     */
    fun repair(text: String, dir: File, pageScan: File?): String {
        val lines = text.split("\n")
        var changed = false
        val rewritten = lines.map { line ->
            val match = IMAGE_DIRECTIVE.find(line) ?: return@map line
            val target = match.groupValues[1]
            if (File(dir, target).isFile) return@map line

            changed = true
            if (pageScan == null) {
                ""
            } else {
                val targetStart = match.range.first + match.value.length - target.length - 1
                line.replaceRange(targetStart, targetStart + target.length, pageScan.name)
            }
        }
        return if (changed) rewritten.joinToString("\n") else text
    }

    /**
     * Every distinct image target referenced by [text] (block and inline forms),
     * in first-appearance order. Used to decide which scans must be
     * materialised next to the assembled book.
     */
    fun targets(text: String): Set<String> =
        text.lineSequence()
            .mapNotNull { IMAGE_DIRECTIVE.find(it)?.groupValues?.getOrNull(1) }
            .toCollection(LinkedHashSet())
}