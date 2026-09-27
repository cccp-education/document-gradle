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
 * Functional tests (TestKit, real Gradle run) for the `translateBookAllLanguages`
 * task (EPIC DOC-BOOK-MULTILANG, US-2).
 *
 * Proves the user-visible outcome: a structured scanned book can be translated
 * into **several** target languages in one run — one `book-<lang>.adoc` per plan
 * entry, each with translated content and a regenerated structure (anchors
 * `[[ref]]`, `:doctype: book`, `toc::[]`), while `book.adoc` is never mutated.
 * A deterministic fake LLM (`translateLlmMode=fake`) keeps the run offline.
 */
class BookMultilangFunctionalTest {

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

    private fun writeBuild(knobs: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-book-multilang\"\n")
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("education.cccp.document")
            }

            document {
                book {
                    pagesDir.set(layout.projectDirectory.dir("pages"))
                    title.set("Multilang Book")
                    author.set("Author")
                    tocFile.set(layout.projectDirectory.file("toc.adoc"))
                    sourceLanguage.set("fr")
                    $knobs
                }
                translation {
                    llmMode.set("fake")
                }
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

    private fun docsDir(): File = projectDir.resolve("build/docs/document")

    @Test
    fun `an explicit subset produces one translated book per requested language`() {
        writeBuild("targetLanguages.set(listOf(\"en\", \"de\"))")

        val result = run("translateBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome)

        listOf("en", "de").forEach { lang ->
            val book = docsDir().resolve("book-$lang.adoc")
            assertTrue(book.isFile, "book-$lang.adoc must be produced — dir: ${docsDir().listFiles()?.joinToString { it.name }}")
            val content = book.readText()
            assertTrue(content.contains("[${lang.uppercase()}]"), "book-$lang must be translated, got:\n$content")
            assertTrue(content.contains("[[1.1]]"), "book-$lang must keep the ref anchors (D1)")
            assertTrue(content.contains(":doctype: book"), "book-$lang must keep the doctype")
            assertTrue(content.contains("toc::[]"), "book-$lang must keep the toc block")
        }
    }

    @Test
    fun `translateToAll produces a book for every catalog language except the source`() {
        writeBuild("translateToAll.set(true)")

        val result = run("translateBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome)

        // en is guaranteed present; fr (the source) must not be produced.
        assertTrue(docsDir().resolve("book-en.adoc").isFile, "translateToAll must include en")
        assertFalse(docsDir().resolve("book-fr.adoc").exists(), "the source language must be excluded")
    }

    @Test
    fun `no knob leaves translateBookAllLanguages inactive`() {
        writeBuild("")

        val result = run("translateBookAllLanguages")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome)

        assertFalse(
            docsDir().listFiles()?.any { it.name.startsWith("book-") && it.name.endsWith(".adoc") } ?: false,
            "no translated book must be produced without a language knob — dir: ${docsDir().listFiles()?.joinToString { it.name }}",
        )
    }

    @Test
    fun `the CLI subset overrides the DSL knobs`() {
        writeBuild("translateToAll.set(true)")

        val result = run("translateBookAllLanguages", "-Pdocument.bookTargetLanguages=es")
        assertEquals(TaskOutcome.SUCCESS, result.task(":translateBookAllLanguages")?.outcome)

        assertTrue(docsDir().resolve("book-es.adoc").isFile, "the CLI subset must produce book-es.adoc")
        assertFalse(docsDir().resolve("book-en.adoc").exists(), "the CLI subset must override translateToAll")
    }

    @Test
    fun `the book dot adoc is never mutated and the outputFileName knob is honoured`() {
        writeBuild("targetLanguages.set(listOf(\"en\"))")
        val docs = docsDir().apply { mkdirs() }
        docs.resolve("book.adoc").writeText("SOURCE BOOK")

        run("translateBookAllLanguages", "-Pdocument.outputFileName=livre")

        assertEquals("SOURCE BOOK", docs.resolve("book.adoc").readText(), "the source book must never be mutated")
        assertTrue(docs.resolve("livre-en.adoc").isFile, "the knob must rename the translated book to livre-en.adoc")
    }
}
