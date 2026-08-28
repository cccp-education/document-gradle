package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Dogfooding functional test — `assembleBook` against the *full* FPA corpus
 * (FPA-BOOK-6 rigorous-layout reconstruction).
 *
 * Unlike [AssembleBookTaskFpaTest] (which uses the 3-page root `toc.adoc`),
 * this test feeds the **rich** TOC (`Devenir_..._FPA_II.adoc`, 116 sections with
 * page numbers) and the complete 203-page scan set, proving that:
 *
 *  1. the book is assembled as a *structured, navigable* AsciiDoc document
 *     (`:toc: macro` + `toc::[]` + `[[ref]]` anchors + `==`/`===`/`====`
 *     semantic headings derived from the TOC refs), recovering the full
 *     table of contents down to the page numbers;
 *  2. the real OCR body content is included (not just TOC headings);
 *  3. OCR / LLM-vision failures are *located* into `book-ocr-issues.json`
 *     with their page number + owning TOC section (ref + title) so a human
 *     can iterate without re-reading the whole book.
 *
 * The FPA content never enters this repository: pages are copied into a
 * throw-away TestKit project at runtime, and the test self-skips (`assumeTrue`)
 * when the corpus is absent.
 */
class AssembleBookTaskFpaRichTocTest {

    companion object {
        private val FPA_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val FPA_RICH_TOC = File(
            FPA_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/Devenir_Formateur_Professionnel_d_Adultes_FPA_II.adoc",
        )
        private val FPA_SCANS = File(
            FPA_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `assembleBook reconstructs the full FPA book from the rich TOC with located OCR failures`() {
        assumeTrue(FPA_RICH_TOC.isFile) { "FPA rich TOC not found at ${FPA_RICH_TOC.absolutePath}" }
        assumeTrue(FPA_SCANS.isDirectory) { "FPA scans not found at ${FPA_SCANS.absolutePath}" }

        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-fpa-rich\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("fpa/pages"))
                    title.set("Devenir Formateur Professionnel d'Adultes FPA II")
                    author.set("Henry-Laurent JANSA")
                    tocFile.set(layout.projectDirectory.file("fpa/toc.adoc"))
                }
            }
            """.trimIndent(),
        )

        // Copy the rich TOC + the full scan set (Ink Economy Law — copy only what
        // the corpus provides; resolution happens by page number, not file name).
        FPA_RICH_TOC.copyTo(projectDir.resolve("fpa/toc.adoc"), overwrite = true)
        val pagesDir = projectDir.resolve("fpa/pages").apply { mkdirs() }
        FPA_SCANS.listFiles { f -> f.extension.equals("adoc", ignoreCase = true) }
            ?.forEach { it.copyTo(pagesDir.resolve(it.name), overwrite = true) }
        assumeTrue(pagesDir.listFiles()?.isNotEmpty() == true) { "no scan pages copied" }

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        // --- structured, navigable book
        val output = projectDir.resolve("build/docs/document/book.adoc")
        assertTrue(output.isFile, "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.contains("= Devenir Formateur Professionnel d'Adultes FPA II"), "title page missing")
        assertTrue(content.contains(":author: Henry-Laurent JANSA"), "author missing")
        assertTrue(content.contains(":toc: macro"), "TOC macro attribute missing")
        assertTrue(content.contains("toc::[]"), "TOC block macro missing")
        // a deep section must be emitted as a level-4 heading with its anchor
        assertTrue(content.contains("[[1.0.2.1]]"), "anchor for 1.0.2.1 must be emitted")
        assertTrue(content.contains("==== 1.0.2.1"), "section 1.0.2.1 must be a ==== heading")
        // real OCR body recovered (page 22 content), not only TOC headings
        assertTrue(content.contains("REAC"), "real OCR body content must be recovered into the book")

        // --- OCR failures located for human iteration
        val report = projectDir.resolve("build/docs/document/book-ocr-issues.json")
        assertTrue(report.isFile, "the OCR issues report must be produced")
        val reportText = report.readText()
        assertTrue(reportText.trim().startsWith("["), "issues report must be a JSON array")
        // page 73 (section 2.1.1) is a known [ILLISIBLE] page in the corpus
        assertTrue(reportText.contains("\"page\": 73"), "page 73 failure must be reported")
        assertTrue(reportText.contains("\"sectionRef\": \"2.1.1\""), "failure must be localised to its TOC section")
        assertTrue(reportText.contains("\"reason\": \"ILLISIBLE\""), "failure reason must be ILLISIBLE")
    }
}
