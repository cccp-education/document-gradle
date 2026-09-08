package document

import java.io.File

/**
 * Locates pages where OCR / LLM-vision failed or produced doubtful content, so
 * a human can iterate on the exact spot instead of re-reading the whole book.
 *
 * A scan page is flagged when:
 * - its body contains the `[ILLISIBLE]` marker (the vision model could not
 *   read it) — [OcrFailureReason.ILLISIBLE];
 * - the body is empty / truncated (below [SHORT_THRESHOLD] characters) —
 *   [OcrFailureReason.TOO_SHORT];
 * - it references an `image::` directive whose target file does not exist
 *   next to the scan — a ghost image ([OcrFailureReason.IMAGE_MISSING]);
 * - it carries a linearised table row whose final cells were likely lost
 *   ([OcrFailureReason.TABLE_SUSPECT]);
 * - its body carries a structural doubt marker emitted by the vision model
 *   (`invalid part`, `out of sequence`) — [OcrFailureReason.STRUCT_SUSPECT].
 *
 * Each issue is localised against the parsed table of contents: the owning
 * [BookSection] (matched by page number) provides the [BookOcrIssue.sectionRef]
 * and [BookOcrIssue.sectionTitle], producing a report like "page 73 — section
 * 2.1.1 (Numérique et chronobiologie)". A single page can carry several issues
 * (e.g. STRUCT_SUSPECT + TABLE_SUSPECT + IMAGE_MISSING) — no signal is lost.
 *
 * Pages that cannot be matched to a TOC section still surface (with a `null`
 * section) so nothing silently disappears. The owning section is resolved by the
 * page number extracted from the scan file name (`014.adoc` -> 14,
 * `055_2.adoc` -> 55).
 *
 * Backward compatible: the whole-page reasons (ILLISIBLE, TOO_SHORT) remain
 * exclusive-first (an illisible page is not additionally probed), while the
 * structured reasons are probed on every readable page.
 *
 * Ink Economy Law: pure read, deterministic, no mutation of any source file.
 */
object BookOcrFailureDetector {

    private const val SHORT_THRESHOLD = 40
    private val ILLISIBLE_MARKERS = listOf("[ILLISIBLE]", "[ILLISIBL")
    private val STRUCT_SUSPECT_MARKERS = listOf("invalid part", "out of sequence")
    private val LINEARISED_TABLE_ROW = Regex("""^\s*\|.*\|\s*$""")

    fun detect(scansDir: File, sections: List<BookSection>): List<BookOcrIssue> {
        if (!scansDir.isDirectory) return emptyList()
        val byPage = sections.associateBy { it.page }
        val issues = mutableListOf<BookOcrIssue>()
        scansDir
            .listFiles { f -> f.isFile && f.extension.equals("adoc", ignoreCase = true) }
            ?.forEach { file ->
                val page = file.nameWithoutExtension.takeWhile { it.isDigit() }.toIntOrNull() ?: return@forEach
                val text = file.readText()
                val owner = byPage[page]
                val wholePageReason =
                    when {
                        ILLISIBLE_MARKERS.any { it in text } -> OcrFailureReason.ILLISIBLE
                        text.trim().length < SHORT_THRESHOLD -> OcrFailureReason.TOO_SHORT
                        else -> null
                    }
                if (wholePageReason != null) {
                    issues += BookOcrIssue(
                        page = page,
                        file = file.name,
                        sectionRef = owner?.ref,
                        sectionTitle = owner?.title,
                        reason = wholePageReason,
                    )
                } else {
                    issues += structuredIssues(file, page, owner, text)
                }
            }
        return issues.sortedBy { it.page }
    }

    private fun structuredIssues(
        file: File,
        page: Int,
        owner: BookSection?,
        text: String,
    ): List<BookOcrIssue> {
        val issues = mutableListOf<BookOcrIssue>()
        STRUCT_SUSPECT_MARKERS.firstOrNull { it in text }?.let { marker ->
            issues += BookOcrIssue(
                page = page,
                file = file.name,
                sectionRef = owner?.ref,
                sectionTitle = owner?.title,
                reason = OcrFailureReason.STRUCT_SUSPECT,
                detail = marker,
            )
        }
        text.lines().firstOrNull { LINEARISED_TABLE_ROW.matches(it) }?.let { row ->
            issues += BookOcrIssue(
                page = page,
                file = file.name,
                sectionRef = owner?.ref,
                sectionTitle = owner?.title,
                reason = OcrFailureReason.TABLE_SUSPECT,
                detail = row.trim(),
            )
        }
        text.lines()
            .mapNotNull { IMAGE_DIRECTIVE.find(it)?.groupValues?.getOrNull(1) }
            .filter { target -> !file.parentFile.resolve(target).isFile }
            .forEach { target ->
                issues += BookOcrIssue(
                    page = page,
                    file = file.name,
                    sectionRef = owner?.ref,
                    sectionTitle = owner?.title,
                    reason = OcrFailureReason.IMAGE_MISSING,
                    detail = target,
                )
            }
        return issues
    }

    private val IMAGE_DIRECTIVE = Regex("""image::([^\[\s]+)\[""")
}