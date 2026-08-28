package document

import java.io.File

/**
 * Locates pages where OCR / LLM-vision failed, so a human can iterate on the
 * exact spot instead of re-reading the whole book.
 *
 * A scan page is flagged when its body contains the `[ILLISIBLE]` marker (the
 * vision model could not read it) or when the body is empty / truncated (below
 * [SHORT_THRESHOLD] characters). Each issue is localised against the parsed
 * table of contents: the owning [BookSection] (matched by page number) provides
 * the [BookOcrIssue.sectionRef] and [BookOcrIssue.sectionTitle], producing a
 * report like "page 73 — section 2.1.1 (Numérique et chronobiologie)".
 *
 * Pages that cannot be matched to a TOC section still surface (with a `null`
 * section) so nothing silently disappears. The owning section is resolved by the
 * page number extracted from the scan file name (`014.adoc` -> 14,
 * `055_2.adoc` -> 55).
 *
 * Ink Economy Law: pure read, deterministic, no mutation of any source file.
 */
object BookOcrFailureDetector {

    private const val SHORT_THRESHOLD = 40
    private val ILLISIBLE_MARKERS = listOf("[ILLISIBLE]", "[ILLISIBL")

    fun detect(scansDir: File, sections: List<BookSection>): List<BookOcrIssue> {
        if (!scansDir.isDirectory) return emptyList()
        val byPage = sections.associateBy { it.page }
        val issues = mutableListOf<BookOcrIssue>()
        scansDir
            .listFiles { f -> f.isFile && f.extension.equals("adoc", ignoreCase = true) }
            ?.forEach { file ->
                val page = file.nameWithoutExtension.takeWhile { it.isDigit() }.toIntOrNull() ?: return@forEach
                val text = file.readText()
                val reason =
                    when {
                        ILLISIBLE_MARKERS.any { it in text } -> OcrFailureReason.ILLISIBLE
                        text.trim().length < SHORT_THRESHOLD -> OcrFailureReason.TOO_SHORT
                        else -> null
                    }
                if (reason != null) {
                    val owner = byPage[page]
                    issues +=
                        BookOcrIssue(
                            page = page,
                            file = file.name,
                            sectionRef = owner?.ref,
                            sectionTitle = owner?.title,
                            reason = reason,
                        )
                }
            }
        return issues.sortedBy { it.page }
    }
}
