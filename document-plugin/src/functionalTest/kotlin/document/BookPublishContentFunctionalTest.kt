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
 * Dogfooding functional test (EPIC DOC-BOOK-PUBLISH, US-4) — the publication
 * fan-out against the real private content corpus, **fully offline**.
 *
 * US-2 proved the mechanism on a synthetic book; this gate closes the loop on
 * the *user-visible* outcome at scale: `translateBookAllLanguages` (fake LLM)
 * then `publishBookAllLanguages` must produce one `book-<lang>.<ext>` per
 * (language, format) pair over a real (bounded) subset of the scanned corpus,
 * each carrying the translated content.
 *
 * The fake LLM keeps the run metered-free (Règle 2). The private corpus is
 * consumed read-only (Règle 7): a bounded, real subset is copied into a
 * throw-away TestKit project (Ink Economy Law). The test self-skips
 * (`assumeTrue`) when the corpus is absent.
 */
class BookPublishContentFunctionalTest {

    companion object {
        private val CONTENT_DIR = File("/home/cheroliv/workspace/office/metiers/FPA")
        private val CONTENT_TOC = File(CONTENT_DIR, "toc.adoc")
        private val CONTENT_SCANS = File(
            CONTENT_DIR,
            "Devenir_Formateur_Professionnel_d_Adultes_FPA_II/scans",
        )

        /** Bounded but real subset — one structured book, a handful of OCR pages. */
        private const val MAX_PAGES = 6

        /** Two target languages × one text format is enough to prove the fan-out. */
        private val TARGETS = listOf("en", "de")
    }

    @TempDir
    lateinit var projectDir: File

    @Test
    fun `the publication fan-out produces one book per language and format over the real corpus`() {
        assumeTrue(CONTENT_TOC.isFile) { "content TOC not found at ${CONTENT_TOC.absolutePath}" }
        assumeTrue(CONTENT_SCANS.isDirectory) { "content scans not found at ${CONTENT_SCANS.absolutePath}" }

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
            rootProject.name = "test-book-publish-content"
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
                    targetLanguages.set(listOf(${TARGETS.joinToString(", ") { "\"$it\"" }}))
                    publishFormats.set(listOf("html"))
                }
                translation {
                    llmMode.set("fake")
                }
            }
            """.trimIndent(),
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir)
            .withArguments("publishBookAllLanguages")
            .withPluginClasspath()
            .build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome, "translation must run first")
        assertEquals(TaskOutcome.SUCCESS, result.task(":publishBookAllLanguages")?.outcome, "publication must succeed")

        val docsDir = projectDir.resolve("build/docs/document")
        TARGETS.forEach { lang ->
            val html = docsDir.resolve("book-$lang.html")
            assertTrue(html.isFile, "book-$lang.html must be produced — dir: ${docsDir.listFiles()?.joinToString { it.name }}")
            assertTrue(html.readText().contains("[${lang.uppercase()}]"), "book-$lang.html must carry the translated content")

            // D1 gate: the published book stems from the structurally regenerated
            // translated book — every *structural* TOC anchor reaches the HTML.
            val missing = tocRefs - anchorRefs(html.readText())
            assertTrue(missing.isEmpty(), "book-$lang.html must carry the structural TOC ids (D1), missing: $missing")
        }
    }

    private fun anchorRefs(content: String): Set<String> =
        Regex("""id="([^"]+)"""").findAll(content).map { it.groupValues[1] }.toSet()
}
