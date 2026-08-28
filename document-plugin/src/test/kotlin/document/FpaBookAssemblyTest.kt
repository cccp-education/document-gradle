package document

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.*

/**
 * TDD anchor for the FPA rigorous-layout reconstruction (FPA-BOOK-6).
 *
 * These tests pin the two building blocks that are MISSING from the existing
 * `Book` domain to dogfood the full 203-page FPA corpus with proper AsciiDoc
 * layout and OCR-failure localisation:
 *
 *  1. [FpaPageResolver] — maps a [BookSection.page] to its OCR scan file, which
 *     lives in `NNN.adoc` form (zero-padded) plus optional `NNN_N.adoc`
 *     continuation splits (the codex/codebase OCR convention for the FPA book),
 *     NOT the `%03d-*.adoc` prefix assumed by [BookAssembler.pageContentResolver].
 *  2. [BookOcrFailureDetector] — locates pages where OCR/LLM-vision failed
 *     (`[ILLISIBLE]` marker or empty/truncated body) and reports the exact
 *     location (page number + owning TOC section ref + title) so a human can
 *     iterate without re-reading the whole book.
 *
 * Both are pure, deterministic, and fully unit-testable (no Gradle TestKit, no
 * real corpus) — baby-step TDD/BDD before wiring into [AssembleBookTask].
 */
class FpaBookAssemblyTest {

    @Test
    fun `FpaPageResolver resolves a section to its zero-padded scan adoc and continuation pages`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        scans.resolve("055.adoc").writeText("== 1-1.2 Title\nBody page 55")
        scans.resolve("055_2.adoc").writeText("Continuation of page 55")
        scans.resolve("014.adoc").writeText("Historique content")

        val resolver = FpaPageResolver(scans)
        val single = BookSection(ref = "1.0.2", title = "Historique", page = 14, pdfFile = "014.pdf")
        assertEquals("Historique content", resolver.content(single).trim())

        val continuation = BookSection(ref = "1.1.2", title = "Differencier", page = 55, pdfFile = "055.pdf")
        val text = resolver.content(continuation)
        assertTrue(text.contains("Body page 55"), "primary scan page must be included")
        assertTrue(text.contains("Continuation of page 55"), "continuation split page must be appended")
    }

    @Test
    fun `FpaPageResolver returns empty string for a missing scan page without throwing`(@TempDir dir: File) {
        val scans = dir.resolve("scans").apply { mkdirs() }
        val resolver = FpaPageResolver(scans)
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
}
