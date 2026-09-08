package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

/**
 * TDD anchor for the scanned-content rigorous-layout reconstruction (BOOK-6).
 *
 * These tests pin the two building blocks that are MISSING from the existing
 * `Book` domain to dogfood the full 203-page scanned corpus with proper AsciiDoc
 * layout and OCR-failure localisation:
 *
 *  1. [ContentPageResolver] — maps a [BookSection.page] to its OCR scan file, which
 *     lives in `NNN.adoc` form (zero-padded) plus optional `NNN_N.adoc`
 *     continuation splits (the codex/codebase OCR convention for the scanned book),
 *     NOT the `%03d-*.adoc` prefix assumed by [BookAssembler.pageContentResolver].
 *  2. [BookOcrFailureDetector] — locates pages where OCR/LLM-vision failed
 *     (`[ILLISIBLE]` marker or empty/truncated body) and reports the exact
 *     location (page number + owning TOC section ref + title) so a human can
 *     iterate without re-reading the whole book.
 *
 * Both are pure, deterministic, and fully unit-testable (no Gradle TestKit, no
 * real corpus) — baby-step TDD/BDD before wiring into [AssembleBookTask].
 */
class ContentBookAssemblyTest {

    @Test
    fun `ContentPageResolver resolves a section to its zero-padded scan adoc and continuation pages`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("055.adoc").writeText("== 1-1.2 Title\nBody page 55")
        scans.resolve("055_2.adoc").writeText("Continuation of page 55")
        scans.resolve("014.adoc").writeText("Historique content")

        val resolver = ContentPageResolver(scans)
        val single = BookSection(ref = "1.0.2", title = "Historique", page = 14, pdfFile = "014.pdf")
        assertEquals("Historique content", resolver.content(single).trim())

        val continuation = BookSection(ref = "1.1.2", title = "Differencier", page = 55, pdfFile = "055.pdf")
        val text = resolver.content(continuation)
        assertTrue(text.contains("Body page 55"), "primary scan page must be included")
        assertTrue(text.contains("Continuation of page 55"), "continuation split page must be appended")
    }

    @Test
    fun `ContentPageResolver returns empty string for a missing scan page without throwing`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        val resolver = ContentPageResolver(scans)
        val missing = BookSection(ref = "1.0.1", title = "Intro", page = 8, pdfFile = "008.pdf")
        assertEquals("", resolver.content(missing))
    }

    @Test
    fun `BookOcrFailureDetector locates illisible pages with their section and reports empty pages`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("073.adoc").writeText("== 2-1.1 ...\nsome text [ILLISIBLE] trailing")
        scans.resolve("040.adoc").writeText("   \n\t  ") // truncated / empty body
        scans.resolve("022.adoc").writeText(
            "== 1-1.1 Identifier\n" +
                "Real content here, long enough to clear the short-body threshold " +
                "used by the detector so this page is not falsely flagged.",
        )

        val sections = listOf(
            BookSection("2.1.1", "Numerique et chronobiologie", 73, "073.pdf"),
            BookSection("1.2.1.1", "Organiser le contenu", 40, "040.pdf"),
            BookSection("1.1.1", "Identifier les referenciels", 22, "022.pdf"),
        )
        val issues = BookOcrFailureDetector.detect(scans, sections)
        assertEquals(2, issues.size, "two pages must be flagged: 73 (illisible) and 40 (too short)")

        val illisible = issues.single { it.page == 73 }
        assertEquals("2.1.1", illisible.sectionRef)
        assertEquals("Numerique et chronobiologie", illisible.sectionTitle)
        assertEquals(OcrFailureReason.ILLISIBLE, illisible.reason)

        val short = issues.single { it.page == 40 }
        assertEquals("1.2.1.1", short.sectionRef)
        assertEquals(OcrFailureReason.TOO_SHORT, short.reason)
    }

    @Test
    fun `BookOcrFailureDetector maps an unowned failed page to a null section for human triage`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("190.adoc").writeText("[ILLISIBLE]")
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        assertEquals(1, issues.size)
        assertEquals(190, issues[0].page)
        assertEquals(null, issues[0].sectionRef)
        assertEquals(OcrFailureReason.ILLISIBLE, issues[0].reason)
    }

    @Test
    fun `IMAGE_MISSING flags an image directive whose referenced file does not exist next to the page`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        // The scan itself exists but the page references an image file that
        // was never captured (ghost image — 3 real RSC-007 findings in the
        // published corpus).
        scans.resolve("010.adoc").writeText(
            "== 1.0.2.1 Diagramme\n" +
                "Regardez le schema :\n" +
                "image::page-010-diagram.png[]\n" +
                "Poursuivons l'analyse du referentiel.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        val missing = issues.filter { it.reason == OcrFailureReason.IMAGE_MISSING }
        assertEquals(1, missing.size, "the ghost image must be flagged")
        assertEquals(10, missing[0].page)
        assertEquals("page-010-diagram.png", missing[0].detail)
    }

    @Test
    fun `IMAGE_MISSING is silent when the referenced image file exists`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("011.adoc").writeText(
            "== 1.0.2.2 Photo\n" +
                "image::page-011-photo.png[]\n" +
                "Legende longue et claire du document numerise.",
        )
        scans.resolve("page-011-photo.png").writeBytes(byteArrayOf(1, 2, 3))
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        assertTrue(issues.none { it.reason == OcrFailureReason.IMAGE_MISSING })
    }

    @Test
    fun `TABLE_SUSPECT flags a linearised table row that lost its final cells`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        // OCR linearised a table: pipe-separated fragments trailing a body line
        // (11 real cases in the published corpus — cells lost at line end).
        scans.resolve("020.adoc").writeText(
            "== 1.1.1 Tableau\n" +
                "La synthese est presentee ci-dessous :\n" +
                "| Modele | Cible | Niveau |\n" +
                "Le texte reprend ensuite sa narration normale avec suffisamment de contenu.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        val suspect = issues.filter { it.reason == OcrFailureReason.TABLE_SUSPECT }
        assertEquals(1, suspect.size, "the linearised table must be flagged")
        assertEquals(20, suspect[0].page)
        assertEquals("| Modele | Cible | Niveau |", suspect[0].detail)
    }

    @Test
    fun `STRUCT_SUSPECT flags an invalid part directive in the body`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("030.adoc").writeText(
            "== 1.2.1 Partie\n" +
                "invalid part — le modele ne peut pas determiner la structure\n" +
                "Le contenu continue malgre cette erreur structurelle evidente.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        val suspect = issues.filter { it.reason == OcrFailureReason.STRUCT_SUSPECT }
        assertEquals(1, suspect.size, "the invalid part marker must be flagged")
        assertEquals(30, suspect[0].page)
    }

    @Test
    fun `STRUCT_SUSPECT flags an out-of-sequence page marker`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("031.adoc").writeText(
            "== 1.2.2 Suite\n" +
                "out of sequence — la page precedente semble manquee\n" +
                "Le reste du texte est intact et assez long pour la lecture humaine.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        val suspect = issues.filter { it.reason == OcrFailureReason.STRUCT_SUSPECT }
        assertEquals(1, suspect.size, "the out-of-sequence marker must be flagged")
        assertEquals(31, suspect[0].page)
    }

    @Test
    fun `one page can carry several distinct issues without losing any signal`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("040.adoc").writeText(
            "== 1.3.1 Multiple\n" +
                "invalid part — structure incertaine\n" +
                "| A | B | C |\n" +
                "image::ghost-040.png[]\n" +
                "Un texte final assez long pour ne pas declencher TOO_SHORT.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList()).filter { it.page == 40 }
        val reasons = issues.map { it.reason }.toSet()
        assertEquals(
            setOf(OcrFailureReason.STRUCT_SUSPECT, OcrFailureReason.TABLE_SUSPECT, OcrFailureReason.IMAGE_MISSING),
            reasons,
            "all three signals must survive on the same page",
        )
    }

    @Test
    fun `issue report carries the detail evidence and keeps whole-page issues unchanged`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("073.adoc").writeText("[ILLISIBLE]")
        scans.resolve("050.adoc").writeText(
            "== 1.4.1 Mixte\n" +
                "| X | Y |\n" +
                "image::ghost-050.png[]\n" +
                "Le corps reste suffisamment long pour la lecture.",
        )
        val issues = BookOcrFailureDetector.detect(scans, emptyList())
        val report = dir.resolve("book-ocr-issues.json")
        BookOcrIssueReport.write(issues, report)
        val json = report.readText()
        // whole-page issue: NO detail field (backward-compatible shape)
        val illisibleEntry = json.lineSequence().single { "\"reason\": \"ILLISIBLE\"" in it }
        assertTrue("detail" !in illisibleEntry, "ILLISIBLE must not carry a detail field")
        // structured issue: detail evidence present
        val missingEntry = json.lineSequence().single { "\"reason\": \"IMAGE_MISSING\"" in it }
        assertTrue("ghost-050.png" in missingEntry, "IMAGE_MISSING must carry the missing image path as detail")
    }
}
