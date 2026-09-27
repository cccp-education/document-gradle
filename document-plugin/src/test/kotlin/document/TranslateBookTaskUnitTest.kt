package document

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * EPIC DOC-BOOK-TRANSLATE — US-2: the `translateBook` task.
 *
 * The task translates the *domain* of the book (node titles + page bodies) and
 * re-assembles it with [BookAssembler], so the structure (anchors, `:doctype:`,
 * `toc::[]`, navigation) is regenerated — never parsed. These ProjectBuilder
 * tests pin the task's action with a deterministic fake LLM (`llmMode=fake`).
 */
class TranslateBookTaskUnitTest {

    private lateinit var tempDir: File
    private lateinit var project: Project

    @BeforeEach
    fun setUp() {
        tempDir = Files.createTempDirectory("translatebooktest").toFile()
        project = ProjectBuilder.builder().build()
        project.pluginManager.apply("education.cccp.document")
    }

    private fun writeBook(): Triple<File, File, File> {
        val pages = File(tempDir, "pages").apply { mkdirs() }
        File(pages, "001-part.adoc").writeText("Part body sentence.")
        File(pages, "002-chapter.adoc").writeText("Chapter body sentence.")
        File(pages, "003-chapter2.adoc").writeText("Second chapter body sentence.")
        val toc = File(tempDir, "toc.adoc").apply {
            writeText(
                """
                | Référence | Sujet / Titre | Page | Fichier
                | 1 | Part I | 1 | 001-part.adoc
                | 1.1 | Chapter One | 2 | 002-chapter.adoc
                | 1.2 | Chapter Two | 3 | 003-chapter2.adoc
                """.trimIndent(),
            )
        }
        val output = File(tempDir, "book-en.adoc")
        return Triple(pages, toc, output)
    }

    private fun task(pages: File, toc: File, output: File): TranslateBookTask {
        val task = project.tasks.getByName("translateBook") as TranslateBookTask
        task.pagesDir.set(pages)
        task.tocFile.set(toc)
        task.title.set("Translated Book")
        task.author.set("Author")
        task.sourceLanguage.set("fr")
        task.targetLanguage.set("en")
        task.llmMode.set("fake")
        task.outputFile.set(output)
        return task
    }

    @Test
    fun `translated book titles and bodies are translated while refs and structure survive`() {
        val (pages, toc, output) = writeBook()

        task(pages, toc, output).translateBook()

        assertTrue(output.isFile, "the translated book must be produced at ${output.absolutePath}")
        val content = output.readText()
        assertTrue(content.contains("Part I [EN]"), "node titles must be translated, got:\n$content")
        assertTrue(content.contains("Chapter body sentence. [EN]"), "page bodies must be translated, got:\n$content")
        assertTrue(content.contains("[[1.1]]"), "ref anchors must be intact, got:\n$content")
        assertTrue(content.contains(":doctype: book"), "doctype must survive, got:\n$content")
        assertTrue(content.contains("toc::[]"), "the toc block must survive, got:\n$content")
    }

    @Test
    fun `the translated book carries the same number of anchors as the source`() {
        val (pages, toc, output) = writeBook()

        task(pages, toc, output).translateBook()

        val content = output.readText()
        val anchors = Regex("""\[\[[^\]]+\]\]""").findAll(content).count()
        assertEquals(3, anchors, "one [[ref]] anchor per section must be regenerated, got:\n$content")
    }

    @Test
    fun `a blank target language leaves the task inactive`() {
        val (pages, toc, output) = writeBook()

        val task = task(pages, toc, output)
        task.targetLanguage.set("")

        task.translateBook()

        assertTrue(!output.exists(), "a blank target language must not write a translated book")
    }

    @Test
    fun `skipExisting preserves an already produced translated book`() {
        val (pages, toc, output) = writeBook()
        output.writeText("SENTINEL")

        val task = task(pages, toc, output)
        task.skipExisting.set(true)

        task.translateBook()

        assertEquals("SENTINEL", output.readText(), "skipExisting must not overwrite an existing translation")
    }
}
