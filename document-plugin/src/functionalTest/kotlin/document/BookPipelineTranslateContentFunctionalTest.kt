package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Dogfooding functional test (EPIC DOC-BOOK-TRANSLATE, US-6) — the *translated*
 * `bookPipeline` against the real private content corpus.
 *
 * US-5 proved the translated *structure* of the real book at scale, but only at
 * the domain level ([BookTranslateContentTest], no files). US-6 closes the loop
 * on the *user-visible* outcome: `bookPipeline` with a target language must
 * publish the **translated** book — `book-en.adoc` converted to HTML/PDF/EPUB —
 * while the source `book.adoc` is never mutated and the structure (anchors,
 * doctype, toc) is regenerated, exactly as in the untranslated real-corpus gate
 * ([BookPipelineContentFunctionalTest]).
 *
 * The fake LLM (`llmMode=fake`) keeps the run metered-free (Règle 2). The
 * private corpus is consumed read-only (Règle 7): a bounded, *real* subset of
 * referenced pages is copied into a throw-away TestKit project (Ink Economy Law
 * — the full-corpus structural gate lives in US-5; this gate only needs the
 * conversion chain). The test self-skips (`assumeTrue`) when the corpus is
 * absent.
 */
class BookPipelineTranslateContentFunctionalTest {

    companion object {
        private val CONTENT_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val CONTENT_TOC = File(CONTENT_DIR, "toc.adoc")
        private val CONTENT_SCANS = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )

        /** Bounded but real subset — one structured book, a handful of OCR pages. */
        private const val MAX_PAGES = 6
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `bookPipeline translates the real content book and publishes translated HTML, PDF and EPUB with the structure preserved`() {
        assumeTrue(CONTENT_TOC.isFile) { "content TOC not found at ${CONTENT_TOC.absolutePath}" }
        assumeTrue(CONTENT_SCANS.isDirectory) { "content scans not found at ${CONTENT_SCANS.absolutePath}" }

        // --- a reduced but real TOC: the header plus the first MAX_PAGES
        // referenced pages that actually exist in the corpus.
        val tocLines = CONTENT_TOC.readText().lines()
        val header = tocLines.firstOrNull { it.trim().startsWith("|") }
        assumeTrue(header != null) { "the content TOC carries no header row" }
        data class TocEntry(val line: String, val ref: String, val fileName: String)
        val referenced = tocLines.mapNotNull { line ->
            val cells = line.trim().split("|").map { it.trim() }.drop(1)
            if (cells.size < 4) return@mapNotNull null
            val ref = cells[0]
            val fileName = cells[3]
            if (Regex("""\d+(\.\d+)*""").matches(ref) && fileName.endsWith(".adoc") &&
                File(CONTENT_SCANS, fileName).isFile
            ) {
                TocEntry(line, ref, fileName)
            } else {
                null
            }
        }.take(MAX_PAGES)
        assumeTrue(referenced.size >= 3) { "at least three referenced real content pages are required" }
        val tocRefs = referenced.map { it.ref }.toSet()

        projectDir.resolve("content").mkdirs()
        projectDir.resolve("content/toc.adoc").writeText(
            (listOf(header) + referenced.map { it.line }).joinToString("\n") + "\n",
        )
        val pagesDir = projectDir.resolve("content/pages").apply { mkdirs() }
        referenced.forEach { (_, _, name) ->
            File(CONTENT_SCANS, name).copyTo(pagesDir.resolve(name), overwrite = true)
        }

        projectDir.resolve("settings.gradle.kts").writeText(
            """
            rootProject.name = "test-bookpipeline-translate-content"
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
                    sourceLanguage.set("fr")
                    targetLanguage.set("en")
                }
                // enrich/collect read this source; the assembled book lives here
                source.set(layout.buildDirectory.file("docs/document/book.adoc"))
                translation {
                    llmMode.set("fake")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("bookPipeline")
            .withPluginClasspath()
            .build()

        for (task in listOf(
            ":assembleBook",
            ":translateBook",
            ":convertDocumentToHtml",
            ":convertDocumentToPdf",
            ":convertDocumentToEpub",
            ":bookPipeline",
        )) {
            assertEquals(TaskOutcome.SUCCESS, result.task(task)?.outcome, "task $task must succeed")
        }

        val docsDir = projectDir.resolve("build/docs/document")
        val sourceBook = docsDir.resolve("book.adoc")
        val translatedBook = docsDir.resolve("book-en.adoc")
        assertTrue(translatedBook.isFile, "the translated book must be produced")
        assertTrue(sourceBook.isFile, "the source book must be assembled")

        // --- the translation is applied to both titles and bodies
        val translated = translatedBook.readText()
        assertTrue(translated.contains("[EN]"), "the translated book must carry translated content")
        assertFalse(sourceBook.readText().contains("[EN]"), "the source book must stay untranslated")

        // --- D1 gate: the structure is regenerated, not parsed — every
        // *structural* TOC anchor is preserved. The gate is scoped to the TOC
        // refs, never to a byte-equal anchor set: an OCR page may carry an
        // in-band anchor artefact (e.g. `[[page-10]]` at the head of a scan),
        // which the pivot legitimately drops (constat #2 — the pivot is lossy on
        // book constructions, the very reason D1 regenerates the structure
        // rather than translating the assembled text).
        val translatedAnchors = anchorRefs(translated)
        val missing = tocRefs - translatedAnchors
        assertTrue(missing.isEmpty(), "the translated book must keep every structural [[ref]] anchor (D1), missing: $missing")
        assertTrue(tocRefs.size >= 3, "the D1 gate must be exercised on a rich anchor set, got: $tocRefs")
        assertTrue(
            anchorRefs(sourceBook.readText()).containsAll(tocRefs),
            "the source book must carry the same structural anchors, proving the set is regenerated in both",
        )
        assertTrue(translated.contains(":doctype: book"), "the doctype must survive the translation")
        assertTrue(translated.contains("toc::[]"), "the toc block must survive the translation")

        // --- the translated book is published in every format
        val html = docsDir.resolve("document.html")
        val pdf = docsDir.resolve("document.pdf")
        val epub = docsDir.resolve("document.epub")
        assertTrue(html.isFile && html.length() > 0, "HTML output must exist and be non-empty")
        assertTrue(pdf.isFile && pdf.length() > 0, "PDF output must exist and be non-empty")
        assertTrue(epub.isFile && epub.length() > 0, "EPUB output must exist and be non-empty")

        val htmlContent = html.readText()
        assertTrue(htmlContent.contains("[EN]"), "the rendered HTML must carry the translated content")
        assertTrue(pdf.readText(Charsets.ISO_8859_1).startsWith("%PDF"), "PDF must be a valid PDF document")
        assertTrue(
            epub.readBytes().take(4).toByteArray().contentEquals("PK\u0003\u0004".toByteArray()),
            "EPUB must be a zip archive",
        )
    }

    private fun anchorRefs(content: String): Set<String> =
        Regex("""\[\[([^\]]+)\]\]""").findAll(content).map { it.groupValues[1] }.toSet()
}
