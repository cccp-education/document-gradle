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
 * *real* private content corpus (BOOK-4, consumer office/metiers).
 *
 * DOC-BOOK-DOMAIN-3 wired the converters of `bookPipeline` to the *assembled*
 * book (see [document.DocumentPlugin]); this test proves the chain
 * `assembleBook -> enrichDocument -> {html,pdf,epub}` actually produces a
 * navigable HTML/PDF/EPUB of the structured scanned-content book. The private pages are
 * copied into a throw-away TestKit project (Rule 7: sources never mutated),
 * and the test self-skips (`assumeTrue`) when the corpus is absent.
 */
class BookPipelineContentFunctionalTest {

    companion object {
        private val CONTENT_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val CONTENT_TOC = File(CONTENT_DIR, "toc.adoc")
        private val CONTENT_SCANS = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `bookPipeline produces a navigable HTML, PDF and EPUB of the real scanned-content book`() {
        assumeTrue(CONTENT_TOC.isFile) { "content TOC not found at ${CONTENT_TOC.absolutePath}" }
        assumeTrue(CONTENT_SCANS.isDirectory) { "content scans not found at ${CONTENT_SCANS.absolutePath}" }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-bookpipeline-content"
            """.trimIndent(),
        )
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }
            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("content/pages"))
                    title.set("Content Book")
                    author.set("Content Author")
                    tocFile.set(layout.projectDirectory.file("content/toc.adoc"))
                }
                // enrich/collect read this source; the assembled book lives here
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
            }
            """.trimIndent(),
        )

        val pagesDir = projectDir.resolve("content/pages").apply { mkdirs() }
        val tocText = CONTENT_TOC.readText()
        CONTENT_TOC.copyTo(projectDir.resolve("content/toc.adoc"), overwrite = true)
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
        assumeTrue(referenced.isNotEmpty()) { "no .adoc page reference found in the content TOC" }
        referenced.forEach { name ->
            val page = File(CONTENT_SCANS, name)
            assumeTrue(page.isFile) { "referenced content page '$name' not found in scans" }
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
    assertTrue(htmlContent.contains("Content Book"), "HTML must contain the book title")
    // the structured assembly emits a hierarchical heading for ref 1.0.0
    // (e.g. "1.0.0. Introduction"); the HTML must also carry navigable
    // anchors (cross-reference ids or heading ids) produced by Asciidoctor.
    assertTrue(htmlContent.contains("1.0.0"), "HTML must carry the 1.0.0 section heading")
    assertTrue(
        htmlContent.contains(" id=\""),
        "HTML must render navigable anchors (cross-reference ids or heading ids)",
    )

    assertTrue(pdf.readText(Charsets.ISO_8859_1).startsWith("%PDF"), "PDF must be a valid PDF document")
    // EPUB is a zip archive
    assertTrue(epub.readBytes().take(4).toByteArray().contentEquals("PK\u0003\u0004".toByteArray()), "EPUB must be a zip archive")
    }
}
