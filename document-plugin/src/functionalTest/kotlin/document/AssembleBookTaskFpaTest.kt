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
 * Dogfooding functional test — `assembleBook` against the *real* FPA corpus
 * (DOC-BOOK-DOMAIN-5, consumer `office/metiers/FPA`).
 *
 * The FPA book (Devenir Formateur Professionnel d'Adultes — FPA II) is a
 * scanned work whose OCR-ed AsciiDoc pages live in `office/metiers/FPA/
 * Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans/` and whose table
 * of contents is `office/metiers/FPA/toc.adoc` (refs `1.0.0`, `1.0.1`,
 * `1.0.2`). No FPA content enters this repository: the pages are *copied*
 * into a throw-away TestKit project at runtime, and the test self-skips
 * (`assumeTrue`) when the corpus is absent (CI, contributor machine).
 *
 * The real FPA TOC is structurally incomplete (no `0.x` front matter, no
 * `9.x` back matter, no `1.0`/`1` intermediate rows) — the LENIENT default
 * must log structural warnings (DOC-BOOK-DOMAIN-6) *without* breaking the
 * build, and the assembled book must still be navigable.
 */
class AssembleBookTaskFpaTest {

    companion object {
        private val FPA_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val FPA_TOC = File(FPA_DIR, "toc.adoc")
        private val FPA_SCANS = File(
            FPA_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `assembleBook dogfoods the real FPA corpus into a structured navigable book`() {
        assumeTrue(FPA_TOC.isFile) { "FPA TOC not found at ${FPA_TOC.absolutePath}" }
        assumeTrue(FPA_SCANS.isDirectory) { "FPA scans not found at ${FPA_SCANS.absolutePath}" }

        // --- materialise the throw-away TestKit project (Rule 7: sources are copied, never mutated)
        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-assemblebook-fpa"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("fpa/pages"))
                    title.set("FPA Book")
                    author.set("FPA Author")
                    tocFile.set(layout.projectDirectory.file("fpa/toc.adoc"))
                }
            }
            """.trimIndent(),
        )

        val pagesDir = projectDir.resolve("fpa/pages").apply { mkdirs() }
        val tocText = FPA_TOC.readText()
        FPA_TOC.copyTo(projectDir.resolve("fpa/toc.adoc"), overwrite = true)
        // copy only the pages the TOC references (Ink Economy Law — delta only),
        // parsing each data row by its four `|`-separated cells (no trailing pipe).
        val referenced = tocText.lines()
            .mapNotNull { line ->
                val cells = line.trim().split("|").map { it.trim() }.drop(1)
                if (cells.size < 4) return@mapNotNull null
                val ref = cells[0]
                val fileName = cells[3]
                if (Regex("""\d+(\.\d+)*""").matches(ref) && fileName.endsWith(".adoc")) {
                    fileName
                } else {
                    null
                }
            }
        assumeTrue(referenced.isNotEmpty()) { "no .adoc page reference found in the FPA TOC" }
        referenced.forEach { name ->
            val page = File(FPA_SCANS, name)
            assumeTrue(page.isFile) { "referenced FPA page '$name' not found in scans" }
            page.copyTo(pagesDir.resolve(name), overwrite = true)
        }

        // --- run the real task
        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("assembleBook")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":assembleBook")?.outcome)

        // --- the assembled book is structured and navigable
        val output = projectDir.resolve("build/docs/document/book.adoc")
        assertTrue(output.isFile, "the assembled book must exist")
        val content = output.readText()
        assertTrue(content.isNotBlank(), "the assembled book must not be blank")
        assertTrue(content.contains("= FPA Book"), "title page missing")
        assertTrue(content.contains(":author: FPA Author"), "author missing")
        assertTrue(content.contains(":toc:"), "TOC attribute missing")
        val tocRefs = Regex("""(?m)^\|\s*(\d[\d.]*)\s*\|""").findAll(tocText).map { it.groupValues[1] }.toList()
        for (ref in tocRefs) {
            assertTrue(content.contains("[[$ref]]"), "anchor [[$ref]] must be emitted")
            // a node at depth k emits "=".repeat(k + 2) equals (BookLayout.heading on node.level + 1)
            val equals = "=".repeat(ref.split('.').size + 1)
            assertTrue(
                content.lines().any { it.startsWith("$equals ") && it.drop(equals.length + 1).startsWith("$ref.") },
                "FPA ref $ref must be emitted as a $equals heading, got:\n${content.take(500)}",
            )
        }
        assertTrue(content.length > 1000, "the assembled book must carry the OCR content, got ${content.length} chars:\n$content")
    }
}
