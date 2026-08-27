package document

import java.io.File

/**
 * Parses a book table of contents (AsciiDoc table) into a list of
 * [BookSection]s — the source of truth for the order and page mapping of a
 * book being assembled from OCR-ed pages.
 *
 * The TOC is the AsciiDoc table produced during [FPA-BOOK-1] (e.g. the root
 * `Devenir_Formateur_Professionnel_d_Adultes_FPA_II.adoc`), with four columns:
 * `Référence | Sujet / Titre | Page | Fichier`.
 *
 * Multi-page rows (`Page = 5, 6, 7, 8` with matching `Fichier` list) are
 * expanded into one [BookSection] per physical page, so the [BookValidator]
 * can check coverage for every page individually. When a row lists more pages
 * than PDF files, the last PDF is reused for the trailing pages (a section may
 * span several scanned pages inside a single PDF).
 *
 * Ink Economy Law: parsing is pure and deterministic — the same TOC always
 * yields the same section list, and no file is mutated.
 */
object BookTocParser {

    private val REF_PATTERN = Regex("""^\d+(\.\d+)*$""")
    private val CELL_SPLIT = Regex("""\|""")

    /**
     * Parses [tocFile] into an ordered list of [BookSection]s.
     *
     * @return the sections (one per physical page); empty if the file is
     *   missing, empty, or contains no valid data rows
     */
    fun parse(tocFile: File): List<BookSection> {
        if (!tocFile.exists() || !tocFile.isFile) return emptyList()
        val lines = tocFile.readLines()
        val sections = mutableListOf<BookSection>()

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (!line.startsWith("|")) continue
            val cells = CELL_SPLIT.split(line).drop(1).map { it.trim() }
            if (cells.size < 4) continue

            val ref = cells[0]
            val title = cells[1]
            if (!REF_PATTERN.matches(ref)) continue
            if (title.isBlank()) continue

            val pages = cells[2].split(",").map { it.trim() }.mapNotNull { it.toIntOrNull() }
            val pdfs = cells[3].split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (pages.isEmpty() || pdfs.isEmpty()) continue

            pages.forEachIndexed { index, page ->
                val pdfFile = pdfs.getOrElse(index) { pdfs.last() }
                sections.add(BookSection(ref = ref, title = title, page = page, pdfFile = pdfFile))
            }
        }

        return sections
    }
}
