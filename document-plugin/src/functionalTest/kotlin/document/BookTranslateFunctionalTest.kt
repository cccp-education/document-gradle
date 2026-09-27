package document

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Functional tests (TestKit, real Gradle run) for the `translateBook` task
 * (EPIC DOC-BOOK-TRANSLATE, US-2).
 *
 * Proves the user-visible outcome: a structured scanned book can be translated
 * into `book-<lang>.adoc` while the structure (anchors `[[ref]]`,
 * `:doctype: book`, `toc::[]`) is regenerated — a deterministic fake LLM
 * (`translateLlmMode=fake`) keeps the test offline. A blank target language
 * leaves the pipeline untouched (backward compatible).
 */
class BookTranslateFunctionalTest {

    @TempDir
    lateinit var projectDir: File

    private fun writePages() {
        val pagesDir = projectDir.resolve("pages").apply { mkdirs() }
        File(pagesDir, "001-part.adoc").writeText("Part body sentence.")
        File(pagesDir, "002-chapter.adoc").writeText("Chapter body sentence.")
        File(pagesDir, "003-chapter2.adoc").writeText("Second chapter body sentence.")
        projectDir.resolve("toc.adoc").writeText(
            """
            | Référence | Sujet / Titre | Page | Fichier
            | 1 | Part I | 1 | 001-part.adoc
            | 1.1 | Chapter One | 2 | 002-chapter.adoc
            | 1.2 | Chapter Two | 3 | 003-chapter2.adoc
            """.trimIndent(),
        )
    }

    private fun writeBuild(targetLine: String, extraBookLine: String = "") {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-translate\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Translated Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    sourceLanguage.set("fr")
                    $targetLine
                }
                translation {
                    llmMode.set("fake")
                }
                $extraBookLine
            }
            """.trimIndent(),
        )
        writePages()
    }

    private fun run(vararg arguments: String) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withArguments(*arguments)
        .withPluginClasspath()
        .build()

    @Test
    fun `translateBook produces book-en dot adoc with translated content and preserved structure`() {
        writeBuild("targetLanguage.set(\"en\")")

        val result = run("translateBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBook")?.outcome)

        val translated = projectDir.resolve("build/docs/document/book-en.adoc")
        assertTrue(
            translated.isFile,
            "the translated book must be produced — dir: " +
                projectDir.resolve("build/docs/document").listFiles()?.joinToString { it.name },
        )
        val content = translated.readText()
        assertTrue(content.contains("Part I [EN]"), "node titles must be translated, got:\n$content")
        assertTrue(content.contains("Chapter body sentence. [EN]"), "page bodies must be translated, got:\n$content")
        assertTrue(content.contains("[[1.1]]"), "ref anchors must be preserved, got:\n$content")
        assertTrue(content.contains(":doctype: book"), "doctype must survive, got:\n$content")
        assertTrue(content.contains("toc::[]"), "the toc block must survive, got:\n$content")
    }

    @Test
    fun `a blank target language leaves translateBook inactive`() {
        writeBuild("")

        val result = run("translateBook")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBook")?.outcome)

        val docsDir = projectDir.resolve("build/docs/document")
        assertFalse(
            docsDir.listFiles()?.any { it.name.startsWith("book-") } ?: false,
            "no translated book must be produced without a target language — dir: " +
                docsDir.listFiles()?.joinToString { it.name },
        )
    }

    @Test
    fun `translateBook honours the outputFileName knob`() {
        writeBuild("targetLanguage.set(\"en\")")

        val result = run("translateBook", "-Pdocument.outputFileName=livre")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBook")?.outcome)

        assertTrue(
            projectDir.resolve("build/docs/document/livre-en.adoc").isFile,
            "the knob must name the output livre-en.adoc — dir: " +
                projectDir.resolve("build/docs/document").listFiles()?.joinToString { it.name },
        )
    }

    @Test
    fun `the CLI target language overrides a blank DSL target`() {
        writeBuild("")

        val result = run("translateBook", "-Pdocument.bookTargetLanguage=de")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBook")?.outcome)

        val translated = projectDir.resolve("build/docs/document/book-de.adoc")
        assertTrue(
            translated.isFile,
            "the CLI must activate translation to book-de.adoc — dir: " +
                projectDir.resolve("build/docs/document").listFiles()?.joinToString { it.name },
        )
        assertTrue(
            translated.readText().contains("Part I [DE]"),
            "the fake service must suffix with the CLI target language",
        )
    }

    @Test
    fun `translateBook keeps the book dot adoc untouched when a target language is set`() {
        writeBuild("targetLanguage.set(\"en\")")
        // A pre-existing assembled book that must NOT be overwritten.
        val docsDir = projectDir.resolve("build/docs/document").apply { mkdirs() }
        docsDir.resolve("book.adoc").writeText("SOURCE BOOK")

        run("translateBook")

        assertEquals("SOURCE BOOK", docsDir.resolve("book.adoc").readText(), "the source book must never be mutated")
        assertTrue(docsDir.resolve("book-en.adoc").isFile, "the translation is written to a distinct file")
    }
}
