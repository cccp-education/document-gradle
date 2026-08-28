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
 * Dogfooding functional test — the real `bookPipeline` task against the
 * *real* FPA corpus (FPA-BOOK-4, consumer `office/metiers/FPA`).
 *
 * DOC-BOOK-DOMAIN-3 wired the converters of `bookPipeline` to the *assembled*
 * book (see [document.DocumentPlugin]); this test proves the chain
 * `assembleBook -> enrichDocument -> {html,pdf,epub}` actually produces a
 * navigable HTML/PDF/EPUB of the structured FPA book. The FPA pages are
 * copied into a throw-away TestKit project (Rule 7: sources never mutated),
 * and the test self-skips (`assumeTrue`) when the corpus is absent.
 */
class BookPipelineFpaFunctionalTest {

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
    fun `bookPipeline produces a navigable HTML, PDF and EPUB of the real FPA book`() {
        assumeTrue(FPA_TOC.isFile) { "FPA TOC not found at ${FPA_TOC.absolutePath}" }
        assumeTrue(FPA_SCANS.isDirectory) { "FPA scans not found at ${FPA_SCANS.absolutePath}" }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-bookpipeline-fpa"
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
                // enrich/collect read this source; the assembled book lives here
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
            }
            """.trimIndent(),
        )

        val pagesDir = projectDir.resolve("fpa/pages").apply { mkdirs() }
        val tocText = FPA_TOC.readText()
        FPA_TOC.copyTo(projectDir.resolve("fpa/toc.adoc"), overwrite = true)
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

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("bookPipeline")
            .withPluginClasspath()
            .build()

        for (task in listOf(
            ":assembleBook",
            ":enrichDocument",
            ":convertDocumentToHtml",
            ":convertDocumentToPdf",
            ":convertDocumentToEpub",
            ":bookPipeline",
        )) {
            assertEquals(TaskOutcome.SUCCESS, result.task(task)?.outcome, "task $task must succeed")
        }

        val docsDir = projectDir.resolve("build/docs/document")
        val html = docsDir.resolve("document.html")
        val pdf = docsDir.resolve("document.pdf")
        val epub = docsDir.resolve("document.epub")

        assertTrue(html.isFile && html.length() > 0, "HTML output must exist and be non-empty")
        assertTrue(pdf.isFile && pdf.length() > 0, "PDF output must exist and be non-empty")
        assertTrue(epub.isFile && epub.length() > 0, "EPUB output must exist and be non-empty")

        val htmlContent = html.readText()
        assertTrue(htmlContent.contains("FPA Book"), "HTML must contain the book title")
        // the structured assembly emits a hierarchical heading for ref 1.0.0
        // (e.g. "1.0.0. Introduction"); the HTML must also carry navigable
        // anchors (cross-reference ids or heading ids) produced by Asciidoctor.
        assertTrue(htmlContent.contains("1.0.0"), "HTML must carry the 1.0.0 section heading")
        assertTrue(
            htmlContent.contains(" id=\""),
            "HTML must render navigable anchors (cross-reference or heading ids)",
        )

        assertTrue(pdf.readText(Charsets.ISO_8859_1).startsWith("%PDF"), "PDF must be a valid PDF document")
        // EPUB is a zip archive
        assertTrue(epub.readBytes().take(4).toByteArray().contentEquals("PK\u0003\u0004".toByteArray()), "EPUB must be a zip archive")
    }
}
